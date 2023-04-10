package com.shatteredpixel.shatteredpixeldungeon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredStatue;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GoldenMimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Wandmaker;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap.Type;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CeremonialCandle;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CorpseDust;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Embers;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Pickaxe;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;

public class SeedFinder {
	enum Mode {
		LIST, SEARCH, TEST
	};

	enum Challenge {
		FOOD("ON DIET", 1), ARMOR("FAITH IS MY ARMOR", 2), POTION("PHARMACOPHOBIA", 4), SEED("BARREN LAND", 8),
		SWARM_INTELLIGENCE("SWARM INTELLIGENCE", 16), DARKNESS("INTO DARKNESS", 32), SCROLL("FORBIDDEN RUNES", 64),
		CHAMPION("HOSTILE CHAMPIONS", 128), BOSSES("BADDER BOSSES", 256);

		private static final Map<String, Challenge> BY_LABEL = new HashMap<>();
		private static final Map<Integer, Challenge> BY_MASK = new HashMap<>();

		static {
			for (Challenge e : values()) {
				BY_LABEL.put(e.label, e);
				BY_MASK.put(e.mask, e);
			}
		}

		public final String label;
		public final Integer mask;

		private Challenge(String label, Integer mask) {
			this.label = label;
			this.mask = mask;
		}

		public static Challenge valueOfLabel(String label) {
			if (BY_LABEL.get(label) == null) {
				throw new IllegalArgumentException();
			}
			return BY_LABEL.get(label);
		}

		public Integer getMask() {
			return this.mask;
		}

		@Override
		public String toString() {
			return this.label;
		}
	}

	public static class Options {
		public static Integer floor;
		public static Mode mode;
		public static String inputFile;
		public static String outputFile;
		public static Long seed;
		public static Set<Challenge> challenges = new HashSet<>();
		public static ArrayList<List<String>> itemList = new ArrayList<>();
	}

	public class CmdArgs {
		public String inputFile;
		public String outputFile;
		public String floor;
		public String seed;
		public String mode;
		public List<String> positional = new ArrayList<>();
	}

	public class HeapItem {
		public Item item;
		public Heap heap;

		public HeapItem(Item item, Heap heap) {
			this.item = item;
			this.heap = heap;
		}
	}

	List<Class<? extends Item>> blacklist = Arrays.asList(Gold.class, Dewdrop.class, IronKey.class, GoldenKey.class,
			CrystalKey.class, EnergyCrystal.class, CorpseDust.class, Embers.class, CeremonialCandle.class,
			Pickaxe.class);;
	Integer challengeMask;

	// TODO: make it parse the item list directly from the arguments
	private CmdArgs parseArgs(String[] args) {
		HashMap<String, String> parsedArguments = new HashMap<>();
		CmdArgs result = new CmdArgs();

		for (int i = 0; i < args.length; i++) {
			// named argument
			if (args[i].startsWith("-")) {
				String argName = args[i].replaceAll("^-+", "");
				String argValue = args[++i];

				if (argValue.startsWith("-")) {
					System.out.printf("Missing value for argument %s\n", argName);
					--i;
					break;
				}

				parsedArguments.put(argName, argValue);
			}
			// positional argument
			else {
				result.positional.add(args[i]);
			}
		}

		for (Entry<String, String> e : parsedArguments.entrySet()) {
			switch (e.getKey()) {
			case "i":
			case "input":
				result.inputFile = e.getValue();
				break;
			case "o":
			case "output":
				result.outputFile = e.getValue();
				break;
			case "f":
			case "floor":
				result.floor = e.getValue();
				break;
			case "s":
			case "seed":
				result.seed = e.getValue();
				break;
			case "m":
			case "mode":
				result.mode = e.getValue();
				break;
			}
		}

		return result;
	}

	private void parseItemList(String str) {
		List<String> itemGroups = Arrays.asList(str.split("\\|"));
		itemGroups.forEach(g -> {
			List<String> items = Arrays.asList(g.split(","));
			Options.itemList.add(items.stream().map(String::trim).collect(Collectors.toList()));
		});
	}

	private void parseInputFile(String inputPath) throws FileNotFoundException {
		Scanner scanner = new Scanner(new File(Options.inputFile));

		while (scanner.hasNextLine()) {
			String optStr = scanner.nextLine().trim();
			if (optStr.startsWith("#")) {
				continue;
			}
			String[] option = optStr.replaceAll("\\s", " ").split(":");
			switch (option[0].trim().toLowerCase()) {
			case "item":
				parseItemList(option[1]);
				break;
			case "floor":
				Options.floor = Integer.parseInt(option[1].trim());
				break;
			case "seed":
				Options.seed = DungeonSeed.convertFromText(option[1].trim());
				break;
			case "mode":
				Options.mode = Mode.valueOf(option[1].trim().toUpperCase());
				break;
			case "output":
				Options.outputFile = option[1].trim();
				break;
			case "challenge":
				Options.challenges.add(Challenge.valueOfLabel(option[1].trim().toUpperCase()));
				break;
			}
		}
		scanner.close();
	}

	private void setupOptions(CmdArgs cmdArgs) {
		// get input file name from cmd arguments, or use default value "in.txt"
		Options.inputFile = cmdArgs.inputFile != null ? cmdArgs.inputFile : "in.txt";
		try {
			parseInputFile(Options.inputFile);
		} catch (FileNotFoundException e) {
			if (Mode.valueOf(cmdArgs.mode.toUpperCase()) != Mode.LIST) {
				System.out.println("Input file not found");
			}
		}

		// set Options object based on cmd arguments
		if (Options.mode == null) {
			Options.mode = Mode.LIST;
		}
		if (cmdArgs.mode != null) {
			Options.mode = Mode.valueOf(cmdArgs.mode.toUpperCase());
		}

		if (Options.outputFile == null) {
			Options.outputFile = "stdout";
		}
		if (cmdArgs.outputFile != null) {
			Options.outputFile = cmdArgs.outputFile;
		}
		if (Options.mode == Mode.SEARCH && Options.outputFile == "stdout") {
			Options.outputFile = "out.txt";
		}

		if (Options.seed == null) {
			Options.seed = 0L;
		}
		if (cmdArgs.seed != null) {
			Options.seed = DungeonSeed.convertFromText(cmdArgs.seed);
		}

		if (Options.floor == null) {
			Options.floor = 24;
		}
		if (cmdArgs.floor != null) {
			Options.floor = Integer.parseInt(cmdArgs.floor);
		}
	}

	private void addTextItems(String caption, ArrayList<HeapItem> items, StringBuilder builder) {
		if (!items.isEmpty()) {
			builder.append(caption + ":\n");

			for (HeapItem item : items) {
				Item i = item.item;
				Heap h = item.heap;

				if (((i instanceof Armor && ((Armor) i).hasGoodGlyph())
						|| (i instanceof Weapon && ((Weapon) i).hasGoodEnchant()) || (i instanceof Ring)
						|| (i instanceof Wand)) && i.cursed) {
					builder.append("- cursed " + i.title().toLowerCase());
				}

				else {
					builder.append("- " + i.title().toLowerCase());
				}

				if (h.type != Type.HEAP) {
					builder.append(" (" + h.title().toLowerCase() + ")");
				}

				builder.append("\n");
			}

			builder.append("\n");
		}
	}

	private void addTextQuest(String caption, ArrayList<Item> items, StringBuilder builder) {
		if (!items.isEmpty()) {
			builder.append(caption + ":\n");

			for (Item i : items) {
				if (i.cursed) {
					builder.append("- cursed " + i.title().toLowerCase() + "\n");
				} else {
					builder.append("- " + i.title().toLowerCase() + "\n");
				}
			}

			builder.append("\n");
		}
	}

	public SeedFinder(String[] args) {
		try {
			setupOptions(parseArgs(args));
			challengeMask = Options.challenges.stream().map(c -> c.getMask())
					.collect(Collectors.summingInt(Integer::intValue));
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

		// overwrite output file
		if (Options.outputFile != "stdout") {
			try (OutputStream os = new FileOutputStream(Options.outputFile, false);
					PrintWriter out = new PrintWriter(os)) {
				out.print("");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		switch (Options.mode) {
		case LIST:
			logSeedItems(Long.toString(Options.seed), Options.floor, challengeMask);
			return;
		case SEARCH:
			if (Options.itemList.size() > 0) {
				for (long currentSeed = Options.seed; currentSeed < DungeonSeed.TOTAL_SEEDS; currentSeed++) {
					String seedCode = DungeonSeed.convertToCode(currentSeed);
					System.out.printf("\rTesting seed %s (%d)...%-10s", seedCode, currentSeed, " ");
					if (testSeed(Long.toString(currentSeed), Options.floor, challengeMask)) {
						System.out.printf("\rFound valid seed %s (%d)%-20s\n", seedCode, currentSeed, " ");
						logSeedItems(Long.toString(currentSeed), Options.floor, challengeMask);
					}
				}
			} else {
				System.out.println("Item list is empty. Unable to search.");
			}
			return;
		case TEST:
			if (testSeed(Long.toString(Options.seed), Options.floor, challengeMask)) {
				System.out.printf("Specified seed %s (%d) is valid\n", DungeonSeed.convertToCode(Options.seed),
						Options.seed);
				logSeedItems(Long.toString(Options.seed), Options.floor, challengeMask);
			} else {
				System.out.printf("Specified seed %s (%d) is NOT valid\n", DungeonSeed.convertToCode(Options.seed),
						Options.seed);
			}
			return;
		default:
			break;
		}
		System.exit(0);
	}

	private ArrayList<Heap> getMobDrops(Level l) {
		ArrayList<Heap> heaps = new ArrayList<>();

		for (Mob m : l.mobs) {
			if (m instanceof Statue) {
				Heap h = new Heap();
				h.items = new LinkedList<>();
				h.items.add(((Statue) m).weapon.identify());
				h.type = Type.STATUE;
				heaps.add(h);
			}

			else if (m instanceof ArmoredStatue) {
				Heap h = new Heap();
				h.items = new LinkedList<>();
				h.items.add(((ArmoredStatue) m).armor.identify());
				h.items.add(((ArmoredStatue) m).weapon.identify());
				h.type = Type.STATUE;
				heaps.add(h);
			}

			else if (m instanceof Mimic) {
				Heap h = new Heap();
				h.items = new LinkedList<>();

				for (Item item : ((Mimic) m).items) {
					h.items.add(item.identify());
				}

				if (m instanceof GoldenMimic) {
					h.type = Type.GOLDEN_MIMIC;
				} else if (m instanceof CrystalMimic) {
					h.type = Type.CRYSTAL_MIMIC;
				} else {
					h.type = Type.MIMIC;
				}
				heaps.add(h);
			}
		}

		return heaps;
	}

	private boolean testSeed(String seed, int floors, int challengeMask) {
		SPDSettings.customSeed(seed);
		SPDSettings.challenges(challengeMask);
		GamesInProgress.selectedClass = HeroClass.WARRIOR;
		Dungeon.init();

		int groupsLeft = Options.itemList.size();
		Boolean[] itemsFound = new Boolean[groupsLeft];
		Arrays.fill(itemsFound, false);

		for (int i = 0; i < floors && groupsLeft > 0; i++) {
			Level l = Dungeon.newLevel();

			ArrayList<Heap> heaps = new ArrayList<>(l.heaps.valueList());
			heaps.addAll(getMobDrops(l));

			for (int j = 0; j < Options.itemList.size() && !itemsFound[j]; j++) {
				boolean isValid = Options.itemList.get(j).stream()
						.filter(testedItem -> heaps.stream().flatMap(h -> h.items.stream()).filter(item -> {
							item.identify();
							return item.title().toLowerCase().contains(testedItem);
						}).findAny().isPresent()).findAny().isPresent();
				if (isValid) {
					itemsFound[j] = isValid;
					groupsLeft--;
				}
			}

			Dungeon.depth++;
		}

		return groupsLeft == 0;
	}

	private void logSeedItems(String seed, int floors, int challengeMask) {
		PrintWriter out = null;
		OutputStream out_fd = System.out;

		try {
			if (Options.outputFile != "stdout") {
				out_fd = new FileOutputStream(Options.outputFile, true);
			}
		} catch (FileNotFoundException e) { // gotta love Java mandatory exceptions
			e.printStackTrace();
		}
		out = new PrintWriter(out_fd);

		SPDSettings.customSeed(seed);
		SPDSettings.challenges(challengeMask);
		GamesInProgress.selectedClass = HeroClass.WARRIOR;
		Dungeon.init();

		out.printf("Items for seed %s (%d):\n\n", DungeonSeed.convertToCode(Dungeon.seed), Dungeon.seed);

		for (int i = 0; i < floors; i++) {
			out.printf("--- floor %d ---\n\n", Dungeon.depth);

			Level l = Dungeon.newLevel();
			ArrayList<Heap> heaps = new ArrayList<>(l.heaps.valueList());
			StringBuilder builder = new StringBuilder();
			ArrayList<HeapItem> scrolls = new ArrayList<>();
			ArrayList<HeapItem> potions = new ArrayList<>();
			ArrayList<HeapItem> equipment = new ArrayList<>();
			ArrayList<HeapItem> rings = new ArrayList<>();
			ArrayList<HeapItem> artifacts = new ArrayList<>();
			ArrayList<HeapItem> wands = new ArrayList<>();
			ArrayList<HeapItem> others = new ArrayList<>();

			// list quest rewards
			if (Ghost.Quest.armor != null) {
				ArrayList<Item> rewards = new ArrayList<>();
				rewards.add(Ghost.Quest.armor.identify());
				rewards.add(Ghost.Quest.weapon.identify());
				Ghost.Quest.complete();

				addTextQuest("Ghost quest rewards", rewards, builder);
			}

			if (Wandmaker.Quest.wand1 != null) {
				ArrayList<Item> rewards = new ArrayList<>();
				rewards.add(Wandmaker.Quest.wand1.identify());
				rewards.add(Wandmaker.Quest.wand2.identify());
				Wandmaker.Quest.complete();

				builder.append("Wandmaker quest item: ");

				switch (Wandmaker.Quest.type) {
				case 1:
				default:
					builder.append("corpse dust\n\n");
					break;
				case 2:
					builder.append("fresh embers\n\n");
					break;
				case 3:
					builder.append("rotberry seed\n\n");
				}

				addTextQuest("Wandmaker quest rewards", rewards, builder);
			}

			if (Imp.Quest.reward != null) {
				ArrayList<Item> rewards = new ArrayList<>();
				rewards.add(Imp.Quest.reward.identify());
				Imp.Quest.complete();

				addTextQuest("Imp quest reward", rewards, builder);
			}

			heaps.addAll(getMobDrops(l));

			// list items
			for (Heap h : heaps) {
				for (Item item : h.items) {
					item.identify();

					if (h.type == Type.FOR_SALE) {
						continue;
					} else if (blacklist.contains(item.getClass())) {
						continue;
					} else if (item instanceof Scroll) {
						scrolls.add(new HeapItem(item, h));
					} else if (item instanceof Potion) {
						potions.add(new HeapItem(item, h));
					} else if (item instanceof MeleeWeapon || item instanceof Armor) {
						equipment.add(new HeapItem(item, h));
					} else if (item instanceof Ring) {
						rings.add(new HeapItem(item, h));
					} else if (item instanceof Artifact) {
						artifacts.add(new HeapItem(item, h));
					} else if (item instanceof Wand) {
						wands.add(new HeapItem(item, h));
					} else {
						others.add(new HeapItem(item, h));
					}
				}
			}

			addTextItems("Scrolls", scrolls, builder);
			addTextItems("Potions", potions, builder);
			addTextItems("Equipment", equipment, builder);
			addTextItems("Rings", rings, builder);
			addTextItems("Artifacts", artifacts, builder);
			addTextItems("Wands", wands, builder);
			addTextItems("Other", others, builder);

			out.print(builder.toString());

			Dungeon.depth++;
		}

		out.print("Undiscovered Artifacts:\n");
		while (true) {
			Item i = Generator.random(Generator.Category.ARTIFACT);
			if (i instanceof Ring) {
				break;
			}
			out.printf("- %s\n", i.title().toLowerCase());
		}
		out.print("\n");

		out.close();
	}

}
