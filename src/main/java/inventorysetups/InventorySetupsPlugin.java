/*
 * Copyright (c) 2019, dillydill123 <https://github.com/dillydill123>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package inventorysetups;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import inventorysetups.ui.InventorySetupPluginPanel;
import inventorysetups.ui.InventorySetupSlot;
import joptsimple.internal.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.vars.InputType;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.tabs.BankSearch;
import net.runelite.client.plugins.runepouch.Runes;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@PluginDescriptor(
		name = "Inventory Setups",
		description = "Save gear setups for specific activities"
)

@Slf4j
public class InventorySetupsPlugin extends Plugin
{

	public static final String CONFIG_GROUP = "inventorysetups";
	public static final String CONFIG_KEY = "setups";
	public static final String CONFIG_KEY_COMPACT_MODE = "compactMode";
	public static final String INV_SEARCH = "inv:";
	private static final int NUM_INVENTORY_ITEMS = 28;
	private static final int NUM_EQUIPMENT_ITEMS = 14;

	@Inject
	private Client client;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	@Getter
	private InventorySetupConfig config;

	@Inject
	@Getter
	private ColorPickerManager colorPickerManager;

	private InventorySetupPluginPanel panel;

	@Getter
	private ArrayList<InventorySetup> inventorySetups;

	private NavigationButton navButton;

	@Inject
	private BankSearch bankSearch;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatboxItemSearch itemSearch;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	private ChatboxTextInput searchInput;

	private static final Varbits[] RUNE_POUCH_AMOUNT_VARBITS =
			{
					Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3
			};
	private static final Varbits[] RUNE_POUCH_RUNE_VARBITS =
			{
					Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3
			};

	private final HotkeyListener returnToSetupsHotkeyListener = new HotkeyListener(() -> config.returnToSetupsHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			panel.returnToOverviewPanel();
		}
	};

	private final HotkeyListener filterBankHotkeyListener = new HotkeyListener(() -> config.filterBankHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			// you must wait at least one game tick otherwise
			// the bank filter will work but then go back to the previous tab.
			// For some reason this can still happen but it is very rare,
			// and only when the user clicks a tab and the hot key extremely shortly after.
			int gameTick = client.getTickCount();
			clientThread.invokeLater(() ->
			{
				int gameTick2 = client.getTickCount();
				if (gameTick2 <= gameTick)
				{
					return false;
				}

				doBankSearch();
				return true;
			});
		}
	};

	@Provides
	InventorySetupConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InventorySetupConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(CONFIG_GROUP))
		{
			if (event.getKey().equals(CONFIG_KEY_COMPACT_MODE))
			{
				panel.rebuild();
			}
		}
	}

	public void switchViews(boolean compactMode)
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_COMPACT_MODE, compactMode);
	}

	@Override
	public void startUp()
	{
		this.panel = new InventorySetupPluginPanel(this, itemManager);
		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "/inventorysetups_icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Inventory Setups")
				.icon(icon)
				.priority(9)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
		keyManager.registerKeyListener(returnToSetupsHotkeyListener);
		keyManager.registerKeyListener(filterBankHotkeyListener);

		// load all the inventory setups from the config file
		clientThread.invokeLater(() ->
		{
			switch (client.getGameState())
			{
				case STARTING:
				case UNKNOWN:
					return false;
			}

			loadConfig();

			SwingUtilities.invokeLater(() ->
			{
				panel.rebuild();
			});

			return true;
		});

	}

	public void addInventorySetup()
	{
		final String name = JOptionPane.showInputDialog(panel,
				"Enter the name of this setup.",
				"Add New Setup",
				JOptionPane.PLAIN_MESSAGE);

		// cancel button was clicked
		if (name == null)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			ArrayList<InventorySetupItem> inv = getNormalizedContainer(InventoryID.INVENTORY);
			ArrayList<InventorySetupItem> eqp = getNormalizedContainer(InventoryID.EQUIPMENT);

			ArrayList<InventorySetupItem> runePouchData = null;
			if (checkIfContainerContainsItem(ItemID.RUNE_POUCH, inv, false))
			{
				runePouchData = getRunePouchData();
			}

			final InventorySetup invSetup = new InventorySetup(inv, eqp, runePouchData, name,
													config.highlightColor(),
													config.highlightStackDifference(),
													config.highlightVariationDifference(),
													config.highlightDifference(),
													config.bankFilter(),
													config.highlightUnorderedDifference());
			addInventorySetupClientThread(invSetup);
		});
	}

	public List<InventorySetup> filterSetups(String textToFilter)
	{
		final String textToFilterLower = textToFilter.toLowerCase();
		return inventorySetups.stream()
				.filter(i -> i.getName().toLowerCase().contains(textToFilterLower))
				.collect(Collectors.toList());
	}

	public void doBankSearch()
	{
		final InventorySetup currentSelectedSetup = panel.getCurrentSelectedSetup();

		if (currentSelectedSetup != null && currentSelectedSetup.isFilterBank())
		{
			client.setVarbit(Varbits.CURRENT_BANK_TAB, 0);
			bankSearch.search(InputType.SEARCH, INV_SEARCH + currentSelectedSetup.getName(), true);
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() != 386)
		{
			return;
		}

		// must be invoked later otherwise causes freezing.
		clientThread.invokeLater(() ->
		{
			// checks to see if the hide worn items button was clicked or bank was opened
			int value = client.getVarcIntValue(386);
			if (value == 0)
			{
				doBankSearch();
			}
		});

	}

	public void resetBankSearch()
	{
		bankSearch.reset(true);
	}

	public ArrayList<InventorySetupItem> getRunePouchData()
	{
		ArrayList<InventorySetupItem> runePouchData = new ArrayList<>();
		for (int i = 0; i < RUNE_POUCH_RUNE_VARBITS.length; i++)
		{
			int runeId = client.getVar(RUNE_POUCH_RUNE_VARBITS[i]);
			Runes rune = Runes.getRune(runeId);
			int runeAmount = rune == null ? 0 : client.getVar(RUNE_POUCH_AMOUNT_VARBITS[i]);
			String runeName = rune == null ? "" : rune.getName();
			int runeItemId = rune == null ? -1 : rune.getItemId();

			runePouchData.add(new InventorySetupItem(runeItemId, runeName, runeAmount));
		}

		return runePouchData;
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		String eventName = event.getEventName();

		int[] intStack = client.getIntStack();
		String[] stringStack = client.getStringStack();
		int intStackSize = client.getIntStackSize();
		int stringStackSize = client.getStringStackSize();

		if (eventName.equals("bankSearchFilter"))
		{
			String search = stringStack[stringStackSize - 1];

			if (search.startsWith(INV_SEARCH))
			{
				final InventorySetup currentSetup = panel.getCurrentSelectedSetup();

				if (currentSetup != null)
				{
					int itemId = intStack[intStackSize - 1];

					if (setupContainsItem(currentSetup, itemId))
					{
						// return true
						intStack[intStackSize - 2] = 1;
					}
					else
					{
						intStack[intStackSize - 2] = 0;
					}
				}
			}
		}
	}

	public void updateCurrentSetup(InventorySetup setup)
	{
		int confirm = JOptionPane.showConfirmDialog(panel,
				"Are you sure you want update this inventory setup?",
				"Warning", JOptionPane.OK_CANCEL_OPTION);

		// cancel button was clicked
		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		// must be on client thread to get names
		clientThread.invokeLater(() ->
		{
			ArrayList<InventorySetupItem> inv = getNormalizedContainer(InventoryID.INVENTORY);
			ArrayList<InventorySetupItem> eqp = getNormalizedContainer(InventoryID.EQUIPMENT);

			ArrayList<InventorySetupItem> runePouchData = null;
			if (checkIfContainerContainsItem(ItemID.RUNE_POUCH, inv, false))
			{
				runePouchData = getRunePouchData();
			}

			setup.updateRunePouch(runePouchData);
			setup.updateInventory(inv);
			setup.updateEquipment(eqp);
			updateConfig();
			panel.refreshCurrentSetup();
		});
	}

	public void updateSlotFromContainer(final InventorySetupSlot slot)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to update from " + (slot.getSlotID().toString().toLowerCase() + "."),
					"Cannot Update Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		final ArrayList<InventorySetupItem> container = getContainerFromSlot(slot);

		// must be invoked on client thread to get the name
		clientThread.invokeLater(() ->
		{
			final ArrayList<InventorySetupItem> playerContainer = getNormalizedContainer(slot.getSlotID());
			final InventorySetupItem newItem = playerContainer.get(slot.getIndexInSlot());

			// update the rune pouch data
			if (!updateIfRunePouch(slot, container.get(slot.getIndexInSlot()), newItem))
			{
				return;
			}

			container.set(slot.getIndexInSlot(), newItem);
			updateConfig();
			panel.refreshCurrentSetup();
		});

	}

	public void updateSlotFromSearch(final InventorySetupSlot slot)
	{

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			JOptionPane.showMessageDialog(panel,
					"You must be logged in to search.",
					"Cannot Search for Item",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		final ArrayList<InventorySetupItem> container = getContainerFromSlot(slot);

		itemSearch
			.tooltipText("Set slot to")
			.onItemSelected((itemId) ->
			{
				clientThread.invokeLater(() ->
				{
					int finalId = itemManager.canonicalize(itemId);

					/*
					 NOTE: the itemSearch shows items from skill guides which can be selected
					 And it does not show equipment variants for worn items that reduce weight.
					 Variation mapping would fix this issue for the inventory,
					 but then it would cause rings, potions, etc to be the same when it may not be desired
					 If a worn item is selected for the equipment, it will not be the correct itemID since
					 only the inventory variant and the skill guide variants show up in the search
					 If there is a way to figure out if and item is a skill guide item, then the inventory
					 issue can be solved. For equipment, you would also need a way to get the equipment variant
					 of a worn item that has weight reduction from the inventory counterpart

					 For now, it's possible that the user will pick a skill guide item, and it will cause highlighting
					 This only occurs if variation differences are turned on. Weight reducing equipment
					 will also be highlighted if selected for equipment if variation differences are turned on.
					*/

					// if the item is stackable, ask for a quantity
					if (itemManager.getItemComposition(finalId).isStackable())
					{
						final int finalIdCopy = finalId;
						searchInput = chatboxPanelManager.openTextInput("Enter amount")
							.addCharValidator(arg -> arg >= 48 && arg <= 57) // only allow numbers (ASCII)
							.onDone((input) ->
							{
								clientThread.invokeLater(() ->
								{
									String inputParsed = input;
									if (inputParsed.length() > 10)
									{
										inputParsed = inputParsed.substring(0, 10);
									}

									// limit to max int value
									long quantityLong = Long.parseLong(inputParsed);
									int quantity = (int) Math.min(quantityLong, Integer.MAX_VALUE);
									quantity = Math.max(quantity, 1);

									final String itemName = itemManager.getItemComposition(finalIdCopy).getName();
									final InventorySetupItem newItem = new InventorySetupItem(finalIdCopy, itemName, quantity);

									// update the rune pouch data
									if (!updateIfRunePouch(slot, container.get(slot.getIndexInSlot()), newItem))
									{
										return;
									}

									container.set(slot.getIndexInSlot(), newItem);
									updateConfig();
									panel.refreshCurrentSetup();

								});
							}).build();
					}
					else
					{
						final String itemName = itemManager.getItemComposition(finalId).getName();
						final InventorySetupItem newItem = new InventorySetupItem(finalId, itemName, 1);

						// update the rune pouch data
						if (!updateIfRunePouch(slot, container.get(slot.getIndexInSlot()), newItem))
						{
							return;
						}

						container.set(slot.getIndexInSlot(), newItem);
						updateConfig();
						panel.refreshCurrentSetup();
					}

				});
			})
			.build();
	}

	public void removeInventorySetup(final InventorySetup setup)
	{
		int confirm = JOptionPane.showConfirmDialog(panel,
				"Are you sure you want to permanently delete this inventory setup?",
				"Warning", JOptionPane.OK_CANCEL_OPTION);

		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		inventorySetups.remove(setup);
		panel.rebuild();
		updateConfig();
	}

	public void updateConfig()
	{
		final Gson gson = new Gson();
		final String json = gson.toJson(inventorySetups);
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
	}

	@Subscribe
	public void onSessionOpen(SessionOpen event)
	{
		final AccountSession session = sessionManager.getAccountSession();
		if (session != null && session.getUsername() != null)
		{
			// config will have changed to new account, load it up
			clientThread.invokeLater(() ->
			{
				loadConfig();
				SwingUtilities.invokeLater(() ->
				{
					panel.rebuild();
				});

				return true;
			});
		}
	}

	@Subscribe
	public void onSessionClose(SessionClose event)
	{
		// config will have changed to local file
		clientThread.invokeLater(() ->
		{
			loadConfig();
			SwingUtilities.invokeLater(() ->
			{
				panel.rebuild();
			});

			return true;
		});
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{

		// check to see that the container is the equipment or inventory
		ItemContainer container = event.getItemContainer();

		if (container == client.getItemContainer(InventoryID.INVENTORY))
		{
			panel.highlightInventory();
		}
		else if (container == client.getItemContainer(InventoryID.EQUIPMENT))
		{
			panel.highlightEquipment();
		}

	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		panel.highlightInventory();
		panel.highlightEquipment();
	}

	public ArrayList<InventorySetupItem> getNormalizedContainer(final InventorySetupSlotID id)
	{
		switch (id)
		{
			case INVENTORY:
				return getNormalizedContainer(InventoryID.INVENTORY);
			case EQUIPMENT:
				return getNormalizedContainer(InventoryID.EQUIPMENT);
			default:
				assert false : "Wrong slot ID!";
				return null;
		}
	}

	public ArrayList<InventorySetupItem> getNormalizedContainer(final InventoryID id)
	{
		assert id == InventoryID.INVENTORY || id == InventoryID.EQUIPMENT : "invalid inventory ID";

		final ItemContainer container = client.getItemContainer(id);

		ArrayList<InventorySetupItem> newContainer = new ArrayList<>();

		Item[] items = null;
		if (container != null)
		{
			items = container.getItems();
		}

		int size = id == InventoryID.INVENTORY ? NUM_INVENTORY_ITEMS : NUM_EQUIPMENT_ITEMS;

		for (int i = 0; i < size; i++)
		{
			if (items == null || i >= items.length)
			{
				// add a "dummy" item to fill the normalized container to the right size
				// this will be useful to compare when no item is in a slot
				newContainer.add(new InventorySetupItem(-1, "", 0));
			}
			else
			{
				final Item item = items[i];
				String itemName = "";

				// only the client thread can retrieve the name. Therefore, do not use names to compare!
				if (client.isClientThread())
				{
					itemName = itemManager.getItemComposition(item.getId()).getName();
				}
				newContainer.add(new InventorySetupItem(item.getId(), itemName, item.getQuantity()));
			}
		}

		return newContainer;
	}

	public void exportSetup(final InventorySetup setup)
	{
		final Gson gson = new Gson();
		final String json = gson.toJson(setup);
		final StringSelection contents = new StringSelection(json);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);

		JOptionPane.showMessageDialog(panel,
				"Setup data was copied to clipboard.",
				"Export Setup Succeeded",
				JOptionPane.PLAIN_MESSAGE);
	}

	public void importSetup()
	{
		try
		{
			final String setup = JOptionPane.showInputDialog(panel,
					"Enter setup data",
					"Import New Setup",
					JOptionPane.PLAIN_MESSAGE);

			// cancel button was clicked
			if (setup == null)
			{
				return;
			}

			final Gson gson = new Gson();
			Type type = new TypeToken<InventorySetup>()
			{

			}.getType();

			final InventorySetup newSetup  = gson.fromJson(setup, type);
			if (newSetup.getRune_pouch() == null && checkIfContainerContainsItem(ItemID.RUNE_POUCH, newSetup.getInventory(), false))
			{
				newSetup.updateRunePouch(getRunePouchData());
			}
			addInventorySetupClientThread(newSetup);
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(panel,
					"Invalid setup data.",
					"Import Setup Failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		bankSearch.reset(true);
	}

	public boolean isHighlightingAllowed()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	private ArrayList<InventorySetupItem> getContainerFromSlot(final InventorySetupSlot slot)
	{
		ArrayList<InventorySetupItem> container = slot.getParentSetup().getInventory();

		if (slot.getSlotID() == InventorySetupSlotID.EQUIPMENT)
		{
			container = slot.getParentSetup().getEquipment();
		}

		assert slot.getParentSetup() == panel.getCurrentSelectedSetup() : "Setup Mismatch";
		assert slot.getIndexInSlot() < container.size() : "Index is greater than container size";

		return container;
	}

	private void loadConfig()
	{
		// serialize the internal data structure from the json in the configuration
		final String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
		if (Strings.isNullOrEmpty(json))
		{
			inventorySetups = new ArrayList<>();
		}
		else
		{
			try
			{
				final Gson gson = new Gson();
				Type type = new TypeToken<ArrayList<InventorySetup>>()
				{

				}.getType();
				inventorySetups = gson.fromJson(json, type);
				for (final InventorySetup setup : inventorySetups)
				{
					if (setup.getRune_pouch() == null && checkIfContainerContainsItem(ItemID.RUNE_POUCH, setup.getInventory(), false))
					{
						setup.updateRunePouch(getRunePouchData());
					}
				}
			}
			catch (Exception e)
			{
				inventorySetups = new ArrayList<>();
			}
		}
	}

	private void addInventorySetupClientThread(final InventorySetup newSetup)
	{
		SwingUtilities.invokeLater(() ->
		{
			inventorySetups.add(newSetup);
			panel.rebuild();

			updateConfig();
		});
	}

	private boolean setupContainsItem(final InventorySetup setup, int itemID)
	{

		// So place holders will show up in the bank.
		itemID = itemManager.canonicalize(itemID);

		// don't variation map unless it's been selected
		if (!setup.isVariationDifference())
		{
			itemID = ItemVariationMapping.map(itemID);
		}

		// check the rune pouch to see if it has the item (runes in this case)
		if (setup.getRune_pouch() != null)
		{
			if (checkIfContainerContainsItem(itemID, setup.getRune_pouch(), false))
			{
				return true;
			}
		}

		return checkIfContainerContainsItem(itemID, setup.getInventory(), setup.isVariationDifference()) ||
				checkIfContainerContainsItem(itemID, setup.getEquipment(), setup.isVariationDifference());
	}

	private boolean checkIfContainerContainsItem(int itemID, final ArrayList<InventorySetupItem> container, boolean isVariationDifference)
	{
		for (final InventorySetupItem item : container)
		{
			if (itemID == getCorrectID(isVariationDifference, item.getId()))
			{
				return true;
			}
		}

		return false;
	}

	private int getCorrectID(boolean variationDifference, int itemId)
	{

		// if variation difference isn't selected, get the canonical ID
		if (!variationDifference)
		{
			return ItemVariationMapping.map(itemManager.canonicalize(itemId));
		}

		int idToCompare = itemId;

		// if it is selected, make sure we aren't showing note form
		ItemComposition comp = itemManager.getItemComposition(itemId);
		if (comp.getNote() != -1)
		{
			idToCompare = comp.getLinkedNoteId();
		}

		return idToCompare;

	}

	private boolean updateIfRunePouch(final InventorySetupSlot slot, final InventorySetupItem oldItem, final InventorySetupItem newItem)
	{

		if (ItemVariationMapping.map(newItem.getId()) == ItemID.RUNE_POUCH)
		{

			if (slot.getSlotID() != InventorySetupSlotID.INVENTORY)
			{

				SwingUtilities.invokeLater(() ->
				{
					JOptionPane.showMessageDialog(panel,
							"You can't have a Rune Pouch there.",
							"Invalid Item",
							JOptionPane.ERROR_MESSAGE);
				});

				return false;
			}

			// only display this message if we aren't replacing a rune pouch with a new rune pouch
			if (slot.getParentSetup().getRune_pouch() != null && ItemVariationMapping.map(oldItem.getId()) != ItemID.RUNE_POUCH)
			{
				SwingUtilities.invokeLater(() ->
				{
					JOptionPane.showMessageDialog(panel,
							"You can't have two Rune Pouches.",
							"Invalid Item",
							JOptionPane.ERROR_MESSAGE);
				});
				return false;
			}

			slot.getParentSetup().updateRunePouch(getRunePouchData());
		}
		else if (ItemVariationMapping.map(oldItem.getId()) == ItemID.RUNE_POUCH)
		{
			// if the old item is a rune pouch, need to update it to null 
			slot.getParentSetup().updateRunePouch(null);
		}

		return true;
	}

}
