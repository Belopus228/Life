package com.belopus.life.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PatternLibrary {

    public enum Category {
        STILL_LIFE("still", "Натюрморты", "Still Lifes"),
        OSCILLATOR("osc", "Осцилляторы", "Oscillators"),
        SPACESHIP("ship", "Космические корабли", "Spaceships"),
        GUN_PUFFER("gun", "Пушки и паровозы", "Guns & Puffers"),
        METHUSELAH("methuselah", "Долгожители", "Methuselahs"),
        INTERACTION("logic", "Конструкции и реакции", "Constructions");

        public final String id;
        public final String titleRu;
        public final String titleEn;

        Category(String id, String titleRu, String titleEn) {
            this.id = id;
            this.titleRu = titleRu;
            this.titleEn = titleEn;
        }

        public String getTitle(boolean ru) {
            return ru ? titleRu : titleEn;
        }
    }

    public static final class PatternEntry {
        public final String id;
        public final Category category;
        public final String nameRu;
        public final String nameEn;
        public final String subtitleRu;
        public final String subtitleEn;
        public final String rle;
        public final int expectedPeriod; // 1 = still life, >1 = oscillator, 0 = evolving/spaceship/gun
        public final boolean isSpaceship;

        private CellRegion cachedRegion;
        private RuleSet cachedRule;

        public PatternEntry(String id, Category category, String nameRu, String nameEn,
                            String subtitleRu, String subtitleEn, String rle,
                            int expectedPeriod, boolean isSpaceship) {
            this.id = id;
            this.category = category;
            this.nameRu = nameRu;
            this.nameEn = nameEn;
            this.subtitleRu = subtitleRu;
            this.subtitleEn = subtitleEn;
            this.rle = rle;
            this.expectedPeriod = expectedPeriod;
            this.isSpaceship = isSpaceship;
        }

        public String getName(boolean ru) {
            return ru ? nameRu : nameEn;
        }

        public String getSubtitle(boolean ru) {
            return ru ? subtitleRu : subtitleEn;
        }

        public CellRegion getRegion() {
            if (cachedRegion == null) {
                RleCodec.DecodedPattern dp = RleCodec.decodeRle(rle);
                cachedRegion = dp.region;
                cachedRule = dp.rule != null ? dp.rule : RuleSet.CONWAY;
            }
            return cachedRegion;
        }

        public RuleSet getRule() {
            if (cachedRule == null) getRegion();
            return cachedRule;
        }
    }

    private static final List<PatternEntry> ENTRIES;

    static {
        List<PatternEntry> list = new ArrayList<>();

        // === STILL LIFES (12) ===
        add(list, "block", Category.STILL_LIFE, "Блок", "Block",
                "4 клетки · простейший натюрморт", "4 cells · simplest still life",
                "x = 2, y = 2, rule = B3/S23\n2o$2o!", 1, false);
        add(list, "beehive", Category.STILL_LIFE, "Улей", "Beehive",
                "6 клеток · самый частый шестиклеточный натюрморт", "6 cells · common 6-cell still life",
                "x = 4, y = 3, rule = B3/S23\nb2o$o2bo$b2o!", 1, false);
        add(list, "loaf", Category.STILL_LIFE, "Каравай", "Loaf",
                "7 клеток · асимметричный натюрморт", "7 cells · asymmetric still life",
                "x = 4, y = 4, rule = B3/S23\nb2o$o2bo$bobo$2bo!", 1, false);
        add(list, "boat", Category.STILL_LIFE, "Лодка", "Boat",
                "5 клеток · единственный 5-клеточный натюрморт", "5 cells · unique 5-cell still life",
                "x = 3, y = 3, rule = B3/S23\n2o$obo$bo!", 1, false);
        add(list, "tub", Category.STILL_LIFE, "Ванна", "Tub",
                "4 клетки · диагональная симметрия", "4 cells · 4-way symmetric",
                "x = 3, y = 3, rule = B3/S23\nbo$obo$bo!", 1, false);
        add(list, "ship", Category.STILL_LIFE, "Корабль", "Ship",
                "6 клеток · лодка с двумя углами", "6 cells · symmetric boat variant",
                "x = 3, y = 3, rule = B3/S23\n2o$obo$b2o!", 1, false);
        add(list, "pond", Category.STILL_LIFE, "Пруд", "Pond",
                "8 клеток · замкнутое кольцо", "8 cells · symmetric ring",
                "x = 4, y = 4, rule = B3/S23\nb2o$o2bo$o2bo$b2o!", 1, false);
        add(list, "barge", Category.STILL_LIFE, "Баржа", "Barge",
                "6 клеток · диагональная структура", "6 cells · diagonal still life",
                "x = 4, y = 4, rule = B3/S23\nbo$obo$bobo$2bo!", 1, false);
        add(list, "long_boat", Category.STILL_LIFE, "Длинная лодка", "Long Boat",
                "7 клеток · вытянутый корабль", "7 cells · elongated boat",
                "x = 4, y = 4, rule = B3/S23\n2o$obo$bobo$2bo!", 1, false);
        add(list, "snake", Category.STILL_LIFE, "Змея", "Snake",
                "6 клеток · изогнутая цепочка", "6 cells · compact chain",
                "x = 4, y = 2, rule = B3/S23\n2obo$ob2o!", 1, false);
        add(list, "eater1", Category.STILL_LIFE, "Пожиратель 1 (Eater)", "Eater 1",
                "7 клеток · поглощает глайдеры и восстанавливается!", "7 cells · absorbs gliders and repairs itself!",
                "x = 4, y = 4, rule = B3/S23\n2o$obo$2bo$2b2o!", 1, false);
        add(list, "eater2", Category.STILL_LIFE, "Пожиратель 2", "Eater 2",
                "19 клеток · усиленный поглотитель глайдеров", "19 cells · heavy-duty glider eater",
                "x = 7, y = 7, rule = B3/S23\n3bob2o$b3ob2o$o$b3ob2o$3bobo$5bo$5b2o!", 1, false);

        // === OSCILLATORS (14) ===
        add(list, "blinker", Category.OSCILLATOR, "Мигалка (Blinker)", "Blinker",
                "Период 2 · простейший осциллятор", "Period 2 · simplest oscillator",
                "x = 3, y = 1, rule = B3/S23\n3o!", 2, false);
        add(list, "toad", Category.OSCILLATOR, "Жаба (Toad)", "Toad",
                "Период 2 · классический 6-клеточный", "Period 2 · classic 6-cell oscillator",
                "x = 4, y = 2, rule = B3/S23\nb3o$3o!", 2, false);
        add(list, "beacon", Category.OSCILLATOR, "Маяк (Beacon)", "Beacon",
                "Период 2 · два взаимодействующих блока", "Period 2 · two interacting blocks",
                "x = 4, y = 4, rule = B3/S23\n2o$2o$2b2o$2b2o!", 2, false);
        add(list, "clock", Category.OSCILLATOR, "Часы (Clock)", "Clock",
                "Период 2 · вращающаяся стрелка", "Period 2 · rotating clock hand",
                "x = 4, y = 4, rule = B3/S23\n2bo$obo$bobo$bo!", 2, false);
        add(list, "bipole", Category.OSCILLATOR, "Биполь (Bipole)", "Bipole",
                "Период 2 · барберпол длины 2", "Period 2 · length-2 barberpole",
                "x = 5, y = 5, rule = B3/S23\n2o$obo2$2bobo$3b2o!", 2, false);
        add(list, "tripole", Category.OSCILLATOR, "Триполь (Tripole)", "Tripole",
                "Период 2 · удлинённый биполь", "Period 2 · extended bipole",
                "x = 6, y = 6, rule = B3/S23\n2o$obo2$2bobo$5bo$4b2o!", 2, false);
        add(list, "pulsar", Category.OSCILLATOR, "Пульсар (Pulsar)", "Pulsar",
                "Период 3 · знаменитый симметричный пульсар", "Period 3 · iconic 4-fold symmetric oscillator",
                "x = 13, y = 13, rule = B3/S23\n2b3o3b3o2$o4bobo4bo$o4bobo4bo$o4bobo4bo$2b3o3b3o2$2b3o3b3o$o4bobo4bo$o4bobo4bo$o4bobo4bo2$2b3o3b3o!", 3, false);
        add(list, "pentadecathlon", Category.OSCILLATOR, "Пентадекатлон", "Pentadecathlon",
                "Период 15 · длиннопериодный из 10 клеток в ряд", "Period 15 · arises from a row of 10 cells",
                "x = 10, y = 3, rule = B3/S23\n2bo4bo$2ob4ob2o$2bo4bo!", 15, false);
        add(list, "figure_eight", Category.OSCILLATOR, "Восьмёрка (Figure 8)", "Figure Eight",
                "Период 8 · два 3×3 блока на углу", "Period 8 · two 3x3 blocks corner-to-corner",
                "x = 6, y = 6, rule = B3/S23\n3o$3o$3o$3b3o$3b3o$3b3o!", 8, false);
        add(list, "koks_galaxy", Category.OSCILLATOR, "Галактика Кока", "Kok's Galaxy",
                "Период 8 · вращающаяся спиральная галактика", "Period 8 · rotating spiral galaxy",
                "x = 9, y = 9, rule = B3/S23\n6ob2o$6ob2o$7b2o$2o5b2o$2o5b2o$2o5b2o$2o$2ob6o$2ob6o!", 8, false);
        add(list, "tumbler", Category.OSCILLATOR, "Акробат (Tumbler)", "Tumbler",
                "Период 14 · открыт Джорджем Коллинзом", "Period 14 · discovered by George Collins",
                "x = 7, y = 6, rule = B3/S23\nb2ob2o$b2ob2o$2bobo$obobobo$obobobo$2o3b2o!", 14, false);
        add(list, "queen_bee_shuttle", Category.OSCILLATOR, "Челнок «Пчеломатка»", "Queen Bee Shuttle",
                "Период 30 · прообраз планерного ружья Госпера", "Period 30 · foundation of Gosper's glider gun",
                "x = 22, y = 7, rule = B3/S23\n9bo$7bobo$6bobo$2o3bo2bo11b2o$2o4bobo11b2o$7bobo$9bo!", 30, false);
        add(list, "tanners_p46", Category.OSCILLATOR, "Осциллятор Таннера (P46)", "Tanner's P46",
                "Период 46 · компактный осциллятор Таннера Якоби (2017)", "Period 46 · compact oscillator by Tanner Jacobi (2017)",
                "x = 13, y = 26, rule = B3/S23\n2b2o9b$2bo10b$3bo9b$2b2o9b$13b$9b2o2b$9bo3b$10bo2b$9b2o2b$b2o10b$b2o6b2o2b$o7bobo2b$b2o6bo3b$b2o7b3o$12bo$13b$13b$13b$13b$13b$13b$b2o10b$b2o2b2o6b$5bobo5b$7bo5b$7b2o4b!", 46, false);
        add(list, "octagon2", Category.OSCILLATOR, "Восьмиугольник-2", "Octagon 2",
                "Период 5 · первый известный осциллятор P=5", "Period 5 · first known period-5 oscillator",
                "x = 8, y = 8, rule = B3/S23\n3b2o$2bo2bo$bo4bo$o6bo$o6bo$bo4bo$2bo2bo$3b2o!", 5, false);

        // === SPACESHIPS (9) ===
        add(list, "glider", Category.SPACESHIP, "Глайдер (Планер)", "Glider",
                "Скорость c/4 по диагонали · символ игры «Жизнь»", "c/4 diagonal · universal hacker emblem",
                "x = 3, y = 3, rule = B3/S23\nbo$2bo$3o!", 0, true);
        add(list, "lwss", Category.SPACESHIP, "Лёгкий корабль (LWSS)", "Lightweight Spaceship (LWSS)",
                "Скорость c/2 по горизонтали · период 4", "c/2 orthogonal · period 4",
                "x = 5, y = 4, rule = B3/S23\nbo2bo$o$o3bo$4o!", 0, true);
        add(list, "mwss", Category.SPACESHIP, "Средний корабль (MWSS)", "Middleweight Spaceship (MWSS)",
                "Скорость c/2 по горизонтали · 11 клеток", "c/2 orthogonal · 11 cells",
                "x = 6, y = 5, rule = B3/S23\n2bo$o3bo$5bo$o4bo$b5o!", 0, true);
        add(list, "hwss", Category.SPACESHIP, "Тяжёлый корабль (HWSS)", "Heavyweight Spaceship (HWSS)",
                "Скорость c/2 по горизонтали · 13 клеток", "c/2 orthogonal · 13 cells",
                "x = 7, y = 5, rule = B3/S23\n2b2o$o4bo$6bo$o5bo$b6o!", 0, true);
        add(list, "copperhead", Category.SPACESHIP, "Медноголовка (Copperhead)", "Copperhead",
                "Скорость c/10 · открыт в 2016 году", "c/10 orthogonal · discovered in 2016",
                "x = 8, y = 12, rule = B3/S23\nb2o2b2o$3b2o$3b2o$obo2bobo$o6bo2$o6bo$b2o2b2o$2b4o2$3b2o$3b2o!", 0, true);
        add(list, "loafer", Category.SPACESHIP, "Лофер (Loafer)", "Loafer",
                "Скорость c/7 ортогонально · 20 клеток", "c/7 orthogonal · compact 20-cell ship",
                "x = 9, y = 9, rule = B3/S23\nb2o2bob2o$o2bo2b2o$bobo$2bo$8bo$6b3o$5bo$6bo$7b2o!", 0, true);
        add(list, "dart", Category.SPACESHIP, "Дротик (Dart)", "Dart",
                "Скорость c/3 ортогонально · период 3", "c/3 orthogonal · period 3 spaceship",
                "x = 15, y = 10, rule = B3/S23\n7bo$6bobo$5bo3bo$6b3o2$4b2o3b2o$2bo3bobo3bo$b2o3bobo3b2o$o5bobo5bo$bob2obobob2obo!", 0, true);
        add(list, "weekender", Category.SPACESHIP, "Уикендер (Weekender)", "Weekender",
                "Скорость 2c/7 · эффектный симметричный корабль", "2c/7 orthogonal · period 7 symmetric ship",
                "x = 16, y = 11, rule = B3/S23\nbo12bo$bo12bo$obo10bobo$bo12bo$bo12bo$2bo3b4o3bo$6b4o$2b4o4b4o2$4bo6bo$5b2o2b2o!", 0, true);
        add(list, "flotilla", Category.SPACESHIP, "Флотилия кораблей", "Spaceship Flotilla",
                "Эскадра из LWSS + MWSS + HWSS в едином строю", "Formation of LWSS, MWSS, and HWSS",
                "x = 7, y = 19, rule = B3/S23\nbo2bo$o$o3bo$4o3$2bo$o3bo$5bo$o4bo$b5o3$2b2o$o4bo$6bo$o5bo$b6o!", 0, true);

        // === GUNS & INFINITE GROWTH (7) ===
        add(list, "gosper_gun", Category.GUN_PUFFER, "Планерное ружьё Госпера", "Gosper Glider Gun",
                "Период 30 · первая доказанная фигура бесконечного роста (1970)", "Period 30 · first known infinite growth pattern (1970)",
                "x = 36, y = 9, rule = B3/S23\n24bo$22bobo$12b2o6b2o12b2o$11bo3bo4b2o12b2o$2o8bo5bo3b2o$2o8bo3bob2o4bobo$10bo5bo7bo$11bo3bo$12b2o!", 0, false);
        add(list, "simkin_gun", Category.GUN_PUFFER, "Ружьё Симкина", "Simkin Glider Gun",
                "Период 120 · самое маленькое по числу клеток (32 клетки)", "Period 120 · smallest glider gun by population (32 cells)",
                "x = 33, y = 21, rule = B3/S23\n2o5b2o$2o5b2o2$4b2o$4b2o5$22b2ob2o$21bo5bo$21bo6bo2b2o$21b3o3bo3b2o$26bo4$20b2o$20bo$21b3o$23bo!", 0, false);
        add(list, "switch_engine_glider", Category.GUN_PUFFER, "Двигатель-переключатель (глайдеры)", "Glider-Producing Switch Engine",
                "Паровоз, выпускающий шлейф глайдеров (бесконечный рост)", "Puffer emitting an infinite trail of gliders",
                "x = 6, y = 4, rule = B3/S23\nbobo$o$bo2bo$3b3o!", 0, false);
        add(list, "switch_engine_block", Category.GUN_PUFFER, "Минимальный бесконечный рост (10 кл.)", "10-Cell Infinite Growth",
                "Всего 10 клеток · минимальное стартовое число клеток для вечного роста", "Only 10 cells · minimal cell count for infinite growth",
                "x = 8, y = 6, rule = B3/S23\n6bo$4bob2o$4bobo$4bo$2bo$obo!", 0, false);
        add(list, "line_infinite", Category.GUN_PUFFER, "Бесконечный рост 5×5", "5x5 Infinite Growth",
                "Компактный квадрат 5×5 (11 клеток), растущий вечно", "Compact 5x5 bounding box (11 cells) with unbounded growth",
                "x = 5, y = 5, rule = B3/S23\n3obo$o$3b2o$b2obo$obobo!", 0, false);
        add(list, "one_row_infinite", Category.GUN_PUFFER, "1-строчный бесконечный рост", "1-Row Infinite Growth",
                "Полоска высотой 1 клетка, порождающая вечный рост", "Height-1 pattern with infinite growth",
                "x = 39, y = 1, rule = B3/S23\n8ob5o3b3o6b7ob5o!", 0, false);
        add(list, "puffer_train", Category.GUN_PUFFER, "Паровоз (Puffer Train)", "Puffer Train",
                "Летит спереди, оставляя за собой стабильный след мигалок", "Travels across the grid leaving blinkers behind",
                "x = 5, y = 18, rule = B3/S23\nbo$2bo$2bo$3o4$o$b2o$2bo$2bo$bo3$bo$2bo$2bo$3o!", 0, false);

        // === METHUSELAHS (8) ===
        add(list, "r_pentomino", Category.METHUSELAH, "R-пентамино", "R-pentomino",
                "5 клеток · стабилизируется лишь на 1103-м поколении!", "5 cells · takes 1,103 generations to stabilize!",
                "x = 3, y = 3, rule = B3/S23\nb2o$2o$bo!", 0, false);
        add(list, "diehard", Category.METHUSELAH, "Крепкий орешек (Diehard)", "Diehard",
                "7 клеток · исчезает полностью ровно на 130-м поколении", "7 cells · vanishes completely after 130 generations",
                "x = 8, y = 3, rule = B3/S23\n6bo$2o$bo3b3o!", 0, false);
        add(list, "acorn", Category.METHUSELAH, "Жёлудь (Acorn)", "Acorn",
                "7 клеток · бурный рост, стабилизация на 5206-м поколении", "7 cells · stabilizes at generation 5,206 with 633 cells",
                "x = 7, y = 3, rule = B3/S23\nbo$3bo$2o2b3o!", 0, false);
        add(list, "b_heptomino", Category.METHUSELAH, "B-гептамино", "B-heptomino",
                "7 клеток · активный долгожитель на 148 поколений", "7 cells · active for 148 generations",
                "x = 4, y = 3, rule = B3/S23\nob2o$3o$bo!", 0, false);
        add(list, "pi_heptomino", Category.METHUSELAH, "Pi-гептамино", "Pi-heptomino",
                "7 клеток · стабилизируется за 173 поколения", "7 cells · stabilizes in 173 generations",
                "x = 3, y = 3, rule = B3/S23\n3o$obo$obo!", 0, false);
        add(list, "rabbits", Category.METHUSELAH, "Кролики (Rabbits)", "Rabbits",
                "9 клеток · стабилизируется на 17,331 поколении!", "9 cells · chaotic explosion lasting 17,331 generations!",
                "x = 7, y = 3, rule = B3/S23\no3b3o$3o2bo$bo!", 0, false);
        add(list, "lidka", Category.METHUSELAH, "Лидка (Lidka)", "Lidka",
                "13 клеток · рекордный долгожитель — 29,055 поколений!", "13 cells · settles after 29,055 generations!",
                "x = 9, y = 15, rule = B3/S23\nbo$obo$bo8$8bo$6bobo$5b2obo2$4b3o!", 0, false);
        add(list, "thunderbird", Category.METHUSELAH, "Буревестник (Thunderbird)", "Thunderbird",
                "6 клеток · симметричный всплеск на 243 поколения", "6 cells · symmetric burst lasting 243 generations",
                "x = 3, y = 5, rule = B3/S23\n3o2$bo$bo$bo!", 0, false);

        // === CONSTRUCTIONS & INTERACTIONS (6) ===
        add(list, "gun_and_eater", Category.INTERACTION, "Ружьё Госпера + Пожиратель", "Gosper Gun + Eater",
                "Замкнутая система: пушка стреляет, пожиратель утилизирует планер", "Closed loop: gun emits gliders and eater cleanly consumes them",
                "x = 42, y = 32, rule = B3/S23\n24bo$22bobo$12b2o6b2o12b2o$11bo3bo4b2o12b2o$2o8bo5bo3b2o$2o8bo3bob2o4bobo$10bo5bo7bo$11bo3bo$12b2o18$37b2o$37bo$38b3o$40bo!", 0, false);
        add(list, "twin_guns_collision", Category.INTERACTION, "Встреча двух планеров (аннигиляция)", "Head-on Glider Collision",
                "Столкновение двух планеров образует стабильный или чистый результат", "Classic glider-glider collision experiment",
                "x = 12, y = 12, rule = B3/S23\nbo$2bo$3o6$9b3o$9bo$10bo!", 0, false);
        add(list, "glider_synthesizes_pulsar", Category.INTERACTION, "Симметричный синтез Пульсара", "Symmetric Pulsar Seed",
                "Простая крестовина, расцветающая в Пульсар", "Simple symmetric seed that blooms into a Pulsar",
                "x = 9, y = 9, rule = B3/S23\n4bo$4bo$4bo2$3o3b3o2$4bo$4bo$4bo!", 0, false);
        add(list, "honeyfarm", Category.INTERACTION, "Пасека (4 улья)", "Honey Farm",
                "Естественный остаток из 4 ульев (24 клетки)", "Four-beehive symmetric constellation",
                "x = 13, y = 13, rule = B3/S23\n5b3o$4bo3bo$3bo5bo$3bo5bo$6bo$2o2b3o2b2o$6bo$3bo5bo$3bo5bo$4bo3bo$5b3o!", 0, false);
        add(list, "highlife_replicator", Category.INTERACTION, "Репликатор HighLife (B36/S23)", "HighLife Replicator (B36/S23)",
                "Уникальная фигура для правила B36/S23: удваивается каждые 12 тактов!", "In rule B36/S23, copies itself along a diagonal endlessly!",
                "x = 5, y = 5, rule = B36/S23\n2b3o$bo2bo$o3bo$o2bo$3o!", 0, false);
        add(list, "max_spacefiller", Category.INTERACTION, "Заполнитель пространства (Spacefiller)", "Spacefiller (Holzwart)",
                "Заполняет всё пространство плотным узором со скоростью c/2 во все стороны", "Expands outward in 4 directions at c/2 filling space to density 1/2",
                "x = 49, y = 26, rule = B3/S23\n20b3o3b3o$19bobbo3bobbo$4o18bo3bo18b4o$o3bo17bo3bo17bo3bo$o8bo12bo3bo12bo8bo$bobbobboobbo25bobboobbobbo$6bo5bo7b3o3b3o7bo5bo$6bo5bo8bo5bo8bo5bo$6bo5bo8b7o8bo5bo$bobbobboobbobboo4bo7bo4boobbobboobbobbo$o8bo3boo4b11o4boo3bo8bo$o3bo9boo17boo9bo3bo$4o11b19o11b4o$16bobo11bobo$19b11o$19bo9bo$20b9o$24bo$20b3o3b3o$22bo3bo2$21b3ob3o$21b3ob3o$20bobooboobo$20b3o3b3o$21bo5bo!", 0, false);

        ENTRIES = Collections.unmodifiableList(list);
    }

    private PatternLibrary() {}

    private static void add(List<PatternEntry> list, String id, Category cat,
                            String nameRu, String nameEn, String subRu, String subEn,
                            String rle, int expectedPeriod, boolean isShip) {
        list.add(new PatternEntry(id, cat, nameRu, nameEn, subRu, subEn, rle, expectedPeriod, isShip));
    }

    public static List<PatternEntry> getAll() {
        return ENTRIES;
    }

    public static Map<Category, List<PatternEntry>> getGrouped() {
        Map<Category, List<PatternEntry>> map = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            map.put(c, new ArrayList<PatternEntry>());
        }
        for (PatternEntry e : ENTRIES) {
            map.get(e.category).add(e);
        }
        return map;
    }

    public static PatternEntry findById(String id) {
        if (id == null) return null;
        for (PatternEntry e : ENTRIES) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }
}
