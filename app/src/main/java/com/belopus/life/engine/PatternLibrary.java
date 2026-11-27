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
        CIRCUITS("circuits", "Транзисторы и сигналы", "Transistors & Signals"),
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

        add(list, "carrier", Category.STILL_LIFE, "Авианосец (Carrier)", "Aircraft Carrier",
                "6 клеток · два угла, поддерживающих друг друга", "6 cells · smallest non-trivial two-component still life",
                "x = 4, y = 3, rule = B3/S23\n2o$o2bo$2b2o!", 1, false);
        add(list, "mango", Category.STILL_LIFE, "Манго (Mango)", "Mango",
                "8 клеток · вытянутый симметричный натюрморт", "8 cells · symmetric eight-cell still life",
                "x = 4, y = 4, rule = B3/S23\nb2o$o2bo$bo2bo$2b2o!", 1, false);
        add(list, "cloverleaf", Category.STILL_LIFE, "Клевер (4 пруда)", "Four Ponds",
                "32 клетки · симметричная композиция из четырёх прудов", "32 cells · four-fold symmetric constellation of ponds",
                "x = 9, y = 9, rule = B3/S23\nb2o3b2o$o2bobo2bo$o2bobo2bo$b2o3b2o2$b2o3b2o$o2bobo2bo$o2bobo2bo$b2o3b2o!", 1, false);

        // === OSCILLATORS (20) ===
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

        add(list, "quadpole", Category.OSCILLATOR, "Квадрополь (Quadpole)", "Quadpole",
                "Период 2 · барберпол длины 4", "Period 2 · length-4 barberpole",
                "x = 7, y = 7, rule = B3/S23\n2o$obo2$2bobo2$4bobo$5b2o!", 2, false);
        add(list, "unix", Category.OSCILLATOR, "Unix (P6)", "Unix",
                "Период 6 · популярный компактный осциллятор 8×8", "Period 6 · compact classic 8x8 oscillator",
                "x = 8, y = 8, rule = B3/S23\nb2o$b2o2$bo$obo$o2bo2b2o$4bob2o$2b2o!", 6, false);
        add(list, "p6_diamond", Category.OSCILLATOR, "Ромб (P6)", "P6 Diamond",
                "Период 6 · симметричный ромб 10×10", "Period 6 · symmetric 10x10 diamond oscillator",
                "x = 10, y = 10, rule = B3/S23\n4b2o$3bo2bo$3b4o$bobo2bobo$o8bo$o8bo$bobo2bobo$3b4o$3bo2bo$4b2o!", 6, false);
        add(list, "p6_star", Category.OSCILLATOR, "Звезда (P6)", "P6 Star",
                "Период 6 · симметричный 16×16 осциллятор", "Period 6 · 4-fold symmetric 16x16 oscillator",
                "x = 16, y = 16, rule = B3/S23\n4b2o4b2o$3bo2bo2bo2bo$3bo3b2o3bo$b2o3bo2bo3b2o$o4bo4bo4bo$o3bo6bo3bo$bobo8bobo$2bo4b2o4bo$2bo4b2o4bo$bobo8bobo$o3bo6bo3bo$o4bo4bo4bo$b2o3bo2bo3b2o$3bo3b2o3bo$3bo2bo2bo2bo$4b2o4b2o!", 6, false);
        add(list, "p9_ring", Category.OSCILLATOR, "Кольцо (P9)", "P9 Ring",
                "Период 9 · редкий нечётный период на четырёх едоках", "Period 9 · symmetric odd-period oscillator",
                "x = 16, y = 11, rule = B3/S23\n2o12b2o$bo12bo$bobo8bobo$2b2o8b2o2$5b6o2$2b2o8b2o$bobo8bobo$bo12bo$2o12b2o!", 9, false);
        add(list, "queen_bee_turn", Category.OSCILLATOR, "Разворот «Пчеломаток» (P88)", "Queen Bee Turn (P88)",
                "Период 88 · кольцо из четырёх челноков, обменивающихся глайдерами", "Period 88 · four queen bee shuttles bouncing gliders in a loop",
                "x = 155, y = 155, rule = B3/S23\n69b2o6b2o$69b2o6b2o7$53b2o38b2o21b2o6b2o$52bobo38bobo20b2o6b2o$52bobob2o4bo22bo4b2obobo20b2o6b2o$53bobobo2b2o24b2o2bobobo21b3o4b3o$17b2o10b2o24bo4b2o24b2o4bo23bo2bo2bo2bo$16bo2bo8bo2bo21bo2bo4bobo20bobo4bo2bo21bob2o2b2obo$16b3o10b3o24bo4b3o6b2ob2ob2o6b3o4bo25b2ob2ob2o$19b10o23bo3bo9bo3b2o4b2o3bo9bo3bo24b2o$18bo2b6o2bo22bo3bo9bo3b2ob2ob2o3bo9bo3bo4b2o18b2o18b2o$18b2o2b4o2b2o26bo34bo7bobo38bobo$53bo2bo34bo2bo4bobob2o32b2obobo$55bo36bo7bobobo32bobobo$53bobobo32bobobo7bo36bo$52bobob2o32b2obobo5b2o8bo2b3o8b3o2bo8b2o$52bobo10b2o26bobo4b3o6b2o3b3o8b3o3b2o6b3o$53b2o9b3o26b2o5b3o6b3o5bo6bo5b3o6b3o$64b2obo32b3o6b2o3b3o8b3o3b2o6b3o$37b2o24bob2o33b3o8bo2b3o8b3o2bo8b3o$37bo26b3o34b2o36b2o$35bobo26bo14b2o21bo36bo$23bo11b2o42bo20bobobo32bobobo$8b2o12b3o41b2o12b3o16bobob2o32b2obobo$8b2o11bo3bo13b2o7bo3b2o6b2o4b2o3bo10bo16bobo18b2o18bobo$21b2ob2o13bo6b3o3bo2bo3bobo5bob2ob2o2b2o22b2o18b2o18b2o$45bo13bo8bo4bo2b2o39b2ob2obo$45b2o5bo2bo2b2o11b3o42bob2o$51bo2bo61bo2bo$51bobo62b3o7b2o$21b2ob2o26bo73bo$8b2o11bo3bo48b2o13b3o35b3o$8b2o12b3o14b2o11b2o20bobo11bo18b2o20bo$23bo7b3o4bo2bo10b2o22bo11bo3bo13bobo14b2o$32bo6b3o34b2o10bo2bobo8bo3bo11bo4b2o$32b2o6bo49bobo2bo4b3o2b2o10bobo$34bo28bo27bo3bo3bo17bo2bo$32bobo28bobo6b2o21bo3b2o17bo$32bobo28b2o3b2o2b2o2b2o14b3o$68b2o2bo2bobo19bo23b2o$38b2o34b3o19bobo15bobo4bobo$38bo35b2o21b2o2b2o11b2o7bo$39b3o59b2o12bo7b2o$41bo$18b2o2b4o2b2o$18bo2b6o2bo89b2o2bo$19b10o11b2o5b2o66b2o2b2o3bo7b2o10b2o$16b3o10b3o9bo5b2o66b2o7bo6bo2bo8bo2bo$16bo2bo8bo2bo9bobo76b4o7b3o2b6o2b3o$17b2o10b2o11b2o18bobo69b2o6b2o$46b2o15b2o68bo10bo$45bobo15bo41b2o26b2obo4bob2o$42b2o2bo58bobo13bo16b2o$9b2o10b2o82bo15b3o$8bo2bo8bo2bo17bo3bo78bo$8b3o2b6o2b3o16bo4bo77b2o$11b2o6b2o18bobobo$10bo10bo16bobobo40bobo53bo$10b2obo4bob2o14bo4bo15bobo23b2o53b3o$15b2o19bo3bo17b2o24bo53b3o$58bo58b2o$38b2o77bobo19bo$92bobo14b2o5b2obo11bob3o2b3o2b3obo$92b2o15b2o6bobo10bo2b3o2b3o2b3o2bo4b2o$15bo77bo24bo10b2o2b2o9b2o2b2o3b2o$14b3o102b2obo7b2o3bo7bo3b2o$14b3o12b2o77bob2o7bo3bo7bo15bo$29bo42bo7bo27bobo10b2o13bo5bo$15bo11bobo42b2o5b3o27bo6b2o18bo5bo$7bob3o2b3o2b3obo3b2o42bobo4b5o26b2o6bo13bo15bo$2o4bo2b3o2b3o2b3o2bo52bobobobo25b2o3b3o5b2o6b2o3bo7bo3b2o$2o3b2o2b2o9b2o2b2o5b2o7bo3b2o31b2o3b2o25b2o3bo7b2o5b2o2b2o9b2o2b2o3b2o$6b2o3bo7bo3b2o6b2o5b3o3b2o84bo2b3o2b3o2b3o2bo4b2o$7bo15bo13bo88b2o3bob3o2b3o2b3obo$12bo5bo18b2o86bobo11bo$12bo5bo13b2o9bob2o78bo$7bo15bo7bo3bo7bobo78b2o12b3o$6b2o3bo7bo3b2o7bob2o8bo93b3o$2o3b2o2b2o9b2o2b2o10bo7b2o93bo$2o4bo2b3o2b3o2b3o2bo10bobo6b2o$7bob3o2b3o2b3obo11bob2o5b2o34bo$15bo19bobo41b2o$36b2o41bobo33bo$14b3o97bobo21b2o$14b3o96bo3bo15b2obo4bob2o$15bo96bo3bo16bo10bo$111bo3bo18b2o6b2o$30b2o78bo3bo16b3o2b6o2b3o$30bo80bobo17bo2bo8bo2bo$31b3o16bo61bo19b2o10b2o$15b2o16bo16b2o56bo$10b2obo4bob2o27bobo55bobo$10bo10bo85b2o$11b2o6b2o80b2o8b2o11b2o10b2o$8b3o2b6o2b3o10b2o2bo20bo40bobo8bobo9bo2bo8bo2bo$8bo2bo8bo2bo6b2o2b2o3bo19b2o41bo3b2o5bo9b3o10b3o$9b2o10b2o7b2o7bo18bobo45b2o5b2o11b10o$35b4o86bo2b6o2bo$125b2o2b4o2b2o$113bo$30b2o7bo12b2o59b3o$31bo7b2o11b2o2b2o58bo$31bobo4bobo15bobo22b2o19bo12b2o$32b2o23bo19b2o2b2o2b2o14b2o$77b2o2bo2bobo14bobo16bobo$36bo17b2o4b2o21b3o34bobo$34bo2bo17bo4b2obo19b2o35bo$35bobo10b2o2b3o9bo49bo6b2o$30b2o4bo11bo3bo8bo15b2o34b3o6bo$30b2o14bobo13bob2o12bo22b2o10bo2bo4b3o7bo$25bo20b2o16b2o12bobo20bo2bo9b2o14b3o12b2o$25b3o51b2o48bo3bo11b2o$28bo72bo2bo24b2ob2o$27b2o7b3o61bo2bo$35bo2bo61bobo$35b2obo42b3o11b2o4bo6b2o$31bob2ob2o39b2o2bo4bo8bo13bo$13b2o18b2o18b2o22b2o2b2ob2obo5bobo5b2o3b3o6bo13b2ob2o$12bobo18b2o18bobo16bo10bo3b2o4b2o6b2o3bo7b2o13bo3bo11b2o$12bobob2o32b2obobo16b3o12b2o41b3o12b2o$13bobobo32bobobo20bo42b2o11bo$15bo36bo21b2o14bo26bobo$14b2o36b2o34b3o26bo$13b3o8bo2b3o8b3o2bo8b3o33b2obo24b2o$13b3o6b2o3b3o8b3o3b2o6b3o32bob2o$13b3o6b3o5bo6bo5b3o6b3o5b2o26b3o9b2o$13b3o6b2o3b3o8b3o3b2o6b3o4bobo26b2o10bobo$14b2o8bo2b3o8b3o2bo8b2o5bobob2o32b2obobo$15bo36bo7bobobo32bobobo$13bobobo32bobobo7bo36bo$12bobob2o32b2obobo4bo2bo34bo2bo$12bobo38bobo7bo34bo26b2o2b4o2b2o$13b2o18b2o18b2o4bo3bo9bo3b2ob2ob2o3bo9bo3bo22bo2b6o2bo$33b2o24bo3bo9bo3b2o4b2o3bo9bo3bo23b10o$30b2ob2ob2o25bo4b3o6b2ob2ob2o6b3o4bo24b3o10b3o$29bob2o2b2obo21bo2bo4bobo20bobo4bo2bo21bo2bo8bo2bo$29bo2bo2bo2bo23bo4b2o24b2o4bo24b2o10b2o$29b3o4b3o21bobobo2b2o24b2o2bobobo$29b2o6b2o20bobob2o4bo22bo4b2obobo$29b2o6b2o20bobo38bobo$29b2o6b2o21b2o38b2o7$76b2o6b2o$76b2o6b2o!", 88, false);

        // === SPACESHIPS (13) ===
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

        add(list, "coe_ship", Category.SPACESHIP, "Корабль Коу (Coe Ship)", "Coe Ship",
                "Скорость c/2 · 31 клетка (открыт Тимом Коу, 1995)", "c/2 orthogonal · 31-cell ship discovered by Tim Coe (1995)",
                "x = 11, y = 11, rule = B3/S23\n4o2b2o$o3b2o$o5bo2bo$bo7bo$3bo2bo3bo$5b2o2bo$4bo$2bo4bo$bo$bo4bo$b5o!", 0, true);
        add(list, "sidecar", Category.SPACESHIP, "Коляска HWSS (Sidecar)", "HWSS Sidecar Escort",
                "Скорость c/2 · тяжёлый корабль с прицепным модулем LWSS", "c/2 orthogonal · HWSS towing an LWSS escort",
                "x = 8, y = 16, rule = B3/S23\n4o$o3bo$o$bo2bo$5bo$bo5bo$o$o6bo$7o4$4o$o3bo$o$bo2bo!", 0, true);
        add(list, "schick_twin", Category.SPACESHIP, "Двойной буксир OWSS", "Double Ship Pushalong",
                "Скорость c/2 · парный сверхтяжёлый эскорт", "c/2 orthogonal · synchronized OWSS + LWSS formation",
                "x = 9, y = 16, rule = B3/S23\n2b4o$2bo3bo$2bo$3bo2bo2$bo6bo$o$o7bo$8o4$2b4o$2bo3bo$2bo$3bo2bo!", 0, true);
        add(list, "pushalong", Category.SPACESHIP, "Толкач MWSS (Pushalong)", "MWSS Pushalong",
                "Скорость c/2 · два средних корабля со стабильным звеном", "c/2 orthogonal · two MWSS linked by a glider-spark tagalong",
                "x = 8, y = 14, rule = B3/S23\n3b2o$bo4bo$o$o5bo$6o2$5bobo$4bo2bo$5bo$3b2o2bo$bo5bo$o$o5bo$6o!", 0, true);

        // === GUNS & INFINITE GROWTH (11) ===
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

        add(list, "ak94", Category.GUN_PUFFER, "Пушка AK-94 (P94)", "AK-94 Glider Gun (P94)",
                "Период 94 · самая компактная истинная пушка периода 94", "Period 94 · smallest known true p94 glider gun",
                "x = 38, y = 25, rule = B3/S23\n7bo7bo7b2o$7b3o5b3o5b2o$10bo7bo$9b2o6b2o16b2o$30b2o2bo2bo$30bobo2b2o$33b2o$5bo28bo$5b3o26bob2o$8bo22b2obo2bo$7b2o22b2ob2o3$17bo$2b2ob2o9bobo10b2o$o2bob2o8bo3bo9bo$2obo11bo3bo10b3o$3bo11bo3bo12bo$3b2o11bobo$b2o2bobo9bo$o2bo2b2o$b2o16b2o$19bo$13b2o5b3o$13b2o7bo!", 0, false);
        add(list, "b52_bomber", Category.GUN_PUFFER, "Бомбардировщик B-52 (P52/P104)", "B-52 Bomber Gun",
                "Период 104 · двухствольная пушка, стреляющая каждые 52 такта", "Period 104 · double-barrelled gun firing every 52 ticks",
                "x = 39, y = 21, rule = B3/S23\nb2o$b2o17bo$19bobo12bobo$20bo12bo$2o7b2o23bo2bo$2obo5b2o23bobobo$3bo23bo7bo2bo$3bo23b2o7b2o$o2bo17b2o5bo$b2o18bo$21b3o$36b2o$36b2o$b2o$o2bo$obobo16bobo4b2o5b2o$bo2bo17b2o4b2o5b2obo$5bo12bo3bo15bo$2bobo12bobo18bo$18bo16bo2bo$36b2o!", 0, false);
        add(list, "p52_gun", Category.GUN_PUFFER, "Пушка P52", "Period-52 Glider Gun",
                "Период 52 · чистая периодическая пушка глайдеров", "Period 52 · true period-52 glider gun",
                "x = 126, y = 97, rule = B3/S23\n64bo$62b3o$61bo$61b2o2$57bo$56bobo$46b3o2bo3bo2bo$43bo2b2obobo4b2o$42bobobo4bo$43bo3b3o31b2o$39bo6bo26bo7bo9bo$38bobo2bo2b2o13bo11b3o2b2obo7b3o$37bo2b4o16b2o14bo3bo7bo$38b2o2b2o2bo13bobo12bo12b2o$40b2o8bo25b3o$40bo8bo$41bo7b3o30bobo$38b3o36b2ob3ob3o$38bo38b2ob2obobo$82b3o$57b2o23b3o$56bobo$50b2o4bo$48bo2bo2b2ob4o$48b2obobobobo2bo13bo$51bobobobo15b2o$51bobob2o16bobo34b2o$37bo14bo57bo$36bo70b2obo$36b3o26b2o2b2o6b3o24bo4bo$65bo2bo2bo4bo18b2o6bo4bo$56bo6bobo10bo2bo14b2o7bo3b3o$55b3o5b2o12b2o17bo7bo4b2o$54b5o8bo2bo14b2o22bo$53b2o3b2o7bob2o9b2o4bo23b3o$54b5o9bob3o7bo5bobo15bo7bo$55b3o7b3o3bobo7b3o3bobo13bobo$56bo8bo7bo9bo5bo2b2o9bo2bo$73b2o11bo2bo2b2o10b2o$53b2o31bo$24bo29bo32bo23b2o$9bo13bo27b3o30b3o9b3o9b2o2bo$9b3o11b3o25bo32bo13bo9bob2o$12bo27bobo23bo30bo12bo$11b2o27b2o23b2o11b2o11b2o17bo$41bo23bobo10bo11bo2bo17b2o$80bo9b2ob2o13b3o2bo$3b2o55b2o14b5o11bo14bo3bo2bo$3bo9b3o45bo13bo11b2o18bob2ob2o$2obo8bo3bo40bo3bobo12b3o7bobo19bo3bo$o2b3o5bo5bo37b3o4b2o15bo6bo22bo2bo3bo$b2o3bo4bo5bo28bob2o4bo21b4o5b2o16bo8b2obobo$3b4o4bo5bo28b2o2bobo2b5o11b2o3bo3b2o7bo12b2o8bo3bo$3bo8bo3bo2b2o18b2o8b2ob3o5bo10b2o4b3o2bo6b3o10bobo6b2o$4b3o6b3o3bobo18bo5b3o2bo3b2o2b2o18bob2o4b2o3bo18b3o3bo$7bo13bo18bobo2bo3b2obobo8bo15bo6bo3b2obo19bobob2o$2b5o14b2o4bobo8b2obobo2b2obo3bo9b2o13b2o5bo2b2obobo21b2o$2bo24b2o8bobobob2obo2bo12bobo21b2obobob2o20bobo$4bo23bo8bo3bo4bobo5bo32bo3bo4bo19b2o$3b2o29b2obo3bob4obob4o16b2o15bo3bob4o20b2o$34bo2bob2obo4bob3o18bo14bobob2obo$35b2obobobob2obo23b3o11b2obob2o2b2obo19b2o$38bo5bob2o25bo14bo4b2ob2o$38bo49b2o25bo3bo$39bo24bo42bo6bo$24bo39b3o38b3o7bo3bo$25bo8b2o16bo14bo21bo14bo11bo$23b3o3b2o2b3o2b2o11b3o12b2o11b2o5b5o13b2o11b2o$29bo2bobo4b2o9b2obo25bo11bo$30bo3bob2obo10b2o37b2o31b2o$33bo3b2o30b2o17b2o32bobo$69b2o16bo17b2o17bo$20bob2o44bo2bo32bo2bo16b2o$18b3obo42bo2bob2o32bo2bo$17bo4bobo27bobo9bo2bo39bo$17b2o3b2obo27b2o10bobo33b2o3bo5b2o$25bo28bo46bo3bo6b2o$25b2o14b2o48b2o9b3o$42bo49bo$39b3o47b3o8b2o$39bo42bo6bo11bo$63b2o18bo14b3o$63bo4b2ob2o8b3o14bo$60b2obobo3b2obo$59bo2bob2obo4bobo$59b2obo3bob4obob3o$62bo3bo4bobo2bob2o$62bobobob2obo2bobob2o$63b2obobo2b2obo3bo$65bobo2bo3bobobo$65bo5b3o6b2o2b2o$64b2o8b2ob3o2bo2bo$71b2o2bobo2b5o$71bob2o4bo15bo$80b3o13bo$82bo11b3o!", 0, false);
        add(list, "vacuum_cleaner", Category.GUN_PUFFER, "Пылесос P46 (Vacuum Cleaner)", "P46 Vacuum Cleaner",
                "Период 46 · двойное ружьё, сдвигающее блоки и ульи", "Period 46 · double p46 gun pulling blocks and beehives",
                "x = 90, y = 97, rule = B3/S23\nb2o23b2o$b2o23bo$24bobo$6bo3b2o12b2o36b2o5b2o$2o2b2obob3o50b2o5b2o$2o2bo4b3o$4bo3bo$5b3o$44b2o$5b3o36b2o$4bo3bo$2o2bo4b3o25b2o$2o2b2obob3o25bobo5b2o$6bo3b2o25bob2o4b2o$21b2o15b2o$b2o35bo$b2o20bo$23bobo12bo23b2o5b2o$20bo17b2o21bo2bo3bo2bo$20bo5bo10bob2o4b2o17b2ob2o$20bo5bo10bobo5b2o16bobobobo$22bo3bo10b2o24bobobobo$22bo38bo9bo$22bo2bo18b2o14bo11bo$22b3o19b2o$65bobo$62bo9bo$62bo3bo5b2o$32bo31b3o6bo$28b2o2bo34b2ob2o$27bo5bo13b2o$26b2o2bobo14b2o$27b2o3bo$28b3o34bo7bo$41b2o3b2o18bobobobo$28b3o9bo2bobo2bo17bobobobo$27b2o3bo6bo9bo15bob2ob2obo$26b2o2bobo5b2o9b2o14b2o5b2o$27bo5bo5bo9bo5bo$16b2o10b2o2bo7bo2bobo2bo5bo$15bobo14bo8b2o3b2o6b3o$15bo$14b2o2$28bo5bo22b2o$26bobo3bobo21b4o$27b2o4b2o20bo2bobo$54bobo2b2o$53bobo$52b2o$43bo8b3o10b2o5b2o$43bobo7bobo9b2o5b2o$43b2o9b2o2$63b2o$39bo5bo16b4o7b2o$40bo5bo14bobobo6bo2b2o11b2o$38b3o3b3o7b2o5b2ob3o4b6o11b2o$54b2o8b2obo5b4o$64b2obo2$64b2obo$64b2obo5b4o$30b2o31bo2bo4b6o11b2o$29bobo32b2o6bo2b2o11b2o$29bo34b2o7b2o$28b2o9b2o$40bo10bo5bo$37b3o9bobo3bobo$37bo12b2o4b2o$62bo$61bobo$61bobo$62bo5$64b2o$64b2o2$67b2o4bo$67b2o3bobo$72bobo$73bo5$75b2o$75b2o2$78b2o$78b2o2$81b2o$81b2o!", 0, false);

        // === METHUSELAHS (12) ===
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

        add(list, "bi_heptomino", Category.METHUSELAH, "Bi-гептамино", "Bi-heptomino",
                "7 клеток · распад за 174 поколения с богатыми обломками", "7 cells · classic 174-generation methuselah",
                "x = 4, y = 3, rule = B3/S23\nob2o$3o$bo!", 0, false);
        add(list, "bunnies", Category.METHUSELAH, "Зайцы (Bunnies)", "Bunnies",
                "9 клеток · развивается 17 332 поколения!", "9 cells · evolves for 17,332 generations!",
                "x = 8, y = 4, rule = B3/S23\no5bo$2bo3bo$2bo2bobo$bobo!", 0, false);
        add(list, "m23334", Category.METHUSELAH, "Долгожитель 23334M", "23334M Methuselah",
                "12 клеток · стабилизируется лишь на 23 334-м поколении", "12 cells · settles at generation 23,334",
                "x = 5, y = 8, rule = B3/S23\n2bo$2o$bo$o2bo$4bo$bo2bo$2bobo$bo!", 0, false);
        add(list, "m47575", Category.METHUSELAH, "Рекордсмен 47575M", "47575M Methuselah",
                "16×16 · эволюционирует целых 47 575 поколений!", "16x16 · astonishing 47,575-generation lifespan!",
                "x = 16, y = 16, rule = B3/S23\n5b2ob2o2bob2o$2bo2bo4b3obo$o2b2obobob2o3bo$bob2o3b2o2b3o$3o8b2o2bo$bob3o4b3ob2o$bob4obob3o$ob2obo2bo3b3o$2b4o2b4o2bo$6b2o2bobob2o$o3bo2bo2b5o$2b4o3b7o$o2b2o2bob2ob3o$o2bo3b2o3bobo$2ob2obob8o$6o3bobo!", 0, false);

        // === TRANSISTORS & SIGNAL LINES (11) ===
        add(list, "signal_line", Category.CIRCUITS, "Линия сигнала (Пушка → Поток → Поглотитель)", "Signal Transmission Line",
                "Прямая линия сигналов из глайдеров (P30) к приёмнику-пожирателю", "Steady P30 glider signal line from emitter gun to Eater sink",
                "x = 104, y = 93, rule = B3/S23\n24bo$22bobo$12b2o6b2o12b2o$11bo3bo4b2o12b2o$2o8bo5bo3b2o$2o8bo3bob2o4bobo$10bo5bo7bo$11bo3bo$12b2o$23bo$24b2o$23b2o6$30bobo$31b2o$31bo5$38bo$39b2o$38b2o6$45bobo$46b2o$46bo5$53bo$54b2o$53b2o6$60bobo$61b2o$61bo5$68bo$69b2o$68b2o6$75bobo$76b2o$76bo5$83bo$84b2o$83b2o6$90bobo$91b2o$91bo10$100b2o$101bo$101bobo$102b2o!", 0, false);
        add(list, "signal_bus_dual", Category.CIRCUITS, "Двойная шина сигналов (2 линии)", "Dual-Rail Signal Bus",
                "Две параллельные синхронизированные линии сигналов", "Two parallel synchronized glider signal lines into dual sinks",
                "x = 144, y = 109, rule = B3/S23\n64bo$62bobo$52b2o6b2o12b2o$51bo3bo4b2o12b2o$40b2o8bo5bo3b2o$40b2o8bo3bob2o4bobo$50bo5bo7bo$51bo3bo$52b2o$63bo$64b2o$63b2o5$24bo$22bobo45bobo$12b2o6b2o12b2o35b2o$11bo3bo4b2o12b2o35bo$2o8bo5bo3b2o$2o8bo3bob2o4bobo$10bo5bo7bo$11bo3bo$12b2o64bo$23bo55b2o$24b2o52b2o$23b2o5$85bobo$30bobo53b2o$31b2o53bo$31bo4$93bo$38bo55b2o$39b2o52b2o$38b2o5$100bobo$45bobo53b2o$46b2o53bo$46bo4$108bo$53bo55b2o$54b2o52b2o$53b2o5$115bobo$60bobo53b2o$61b2o53bo$61bo4$123bo$68bo55b2o$69b2o52b2o$68b2o5$130bobo$75bobo53b2o$76b2o53bo$76bo5$83bo$84b2o$83b2o2$140b2o$141bo$141bobo$142b2o$90bobo$91b2o$91bo10$100b2o$101bo$101bobo$102b2o!", 0, false);
        add(list, "signal_cross_annihilator", Category.CIRCUITS, "Перекрёсток 90° (Аннигилятор линий)", "90-Deg Signal Annihilator",
                "Две встречные линии сигналов гасят друг друга без единого следа", "Two perpendicular signal lines colliding cleanly into zero debris",
                "x = 141, y = 50, rule = B3/S23\n24bo91bo$22bobo91bobo$12b2o6b2o12b2o69b2o12b2o6b2o$11bo3bo4b2o12b2o69b2o12b2o4bo3bo$2o8bo5bo3b2o97b2o3bo5bo8b2o$2o8bo3bob2o4bobo91bobo4b2obo3bo8b2o$10bo5bo7bo91bo7bo5bo$11bo3bo109bo3bo$12b2o113b2o$23bo93bo$24b2o89b2o$23b2o91b2o6$30bobo75bobo$31b2o75b2o$31bo77bo5$38bo63bo$39b2o59b2o$38b2o61b2o6$45bobo45bobo$46b2o45b2o$46bo47bo5$53bo33bo$54b2o29b2o$53b2o31b2o6$60bobo15bobo$61b2o15b2o$61bo17bo!", 0, false);
        add(list, "transistor_not_open", Category.CIRCUITS, "Транзистор «НЕ» — Открыт (Затвор=0 → Выход=1)", "NOT Transistor (Open: Gate=0 -> Out=1)",
                "Сигнальная линия проходит в выходную линию; сотрите 4×4 заслонку справа, чтобы закрыть!", "Supply line flows into output line; erase the small 4x4 Gate Eater to switch OFF!",
                "x = 141, y = 113, rule = B3/S23\n24bo91bo$22bobo91bobo$12b2o6b2o12b2o69b2o12b2o6b2o$11bo3bo4b2o12b2o69b2o12b2o4bo3bo$2o8bo5bo3b2o97b2o3bo5bo8b2o$2o8bo3bob2o4bobo91bobo4b2obo3bo8b2o$10bo5bo7bo91bo7bo5bo$11bo3bo109bo3bo$12b2o113b2o$23bo91bo$24b2o89b3o$23b2o$113b2o$113b2o2$113b2o$113bo$30bobo78bobo$31b2o78b2o$31bo5$38bo$39b2o$38b2o6$45bobo$46b2o$46bo5$53bo$54b2o$53b2o6$60bobo$61b2o$61bo5$68bo$69b2o$68b2o6$75bobo$76b2o$76bo5$83bo$84b2o$83b2o6$90bobo$91b2o$91bo5$98bo$99b2o$98b2o6$105bobo$106b2o$106bo15$120b2o$121bo$121bobo$122b2o!", 0, false);
        add(list, "transistor_not_closed", Category.CIRCUITS, "Транзистор «НЕ» — Закрыт (Затвор=1 → Выход=0)", "NOT Transistor (Closed: Gate=1 -> Out=0)",
                "Управляющая линия затвора перерезает поток питания под углом 90°", "Control line intercepts and cuts off the supply signal line cleanly at 90 deg",
                "x = 141, y = 113, rule = B3/S23\n24bo91bo$22bobo91bobo$12b2o6b2o12b2o69b2o12b2o6b2o$11bo3bo4b2o12b2o69b2o12b2o4bo3bo$2o8bo5bo3b2o97b2o3bo5bo8b2o$2o8bo3bob2o4bobo91bobo4b2obo3bo8b2o$10bo5bo7bo91bo7bo5bo$11bo3bo109bo3bo$12b2o113b2o$23bo93bo$24b2o89b2o$23b2o91b2o6$30bobo75bobo$31b2o75b2o$31bo77bo5$38bo63bo$39b2o59b2o$38b2o61b2o6$45bobo45bobo$46b2o45b2o$46bo47bo5$53bo33bo$54b2o29b2o$53b2o31b2o6$60bobo15bobo$61b2o15b2o$61bo17bo60$120b2o$121bo$121bobo$122b2o!", 0, false);
        add(list, "transistor_and_on", Category.CIRCUITS, "Транзистор «И» — Открыт (A=1, B=1 → Линия=1)", "AND Transistor (ON: A=1, B=1 -> Out=1)",
                "3 пушки в чистом B3/S23: вход A открывает путь длинной выходной линии B!", "3-gun pure Conway AND gate: Input A unlocks the long Output signal line B!",
                "x = 186, y = 133, rule = B3/S23\n24bo91bo$22bobo91bobo$12b2o6b2o12b2o69b2o12b2o6b2o$11bo3bo4b2o12b2o69b2o12b2o4bo3bo$2o8bo5bo3b2o97b2o3bo5bo8b2o$2o8bo3bob2o4bobo91bobo4b2obo3bo8b2o$10bo5bo7bo91bo7bo5bo$11bo3bo109bo3bo$12b2o113b2o$23bo93bo$24b2o89b2o$23b2o91b2o6$30bobo75bobo$31b2o75b2o$31bo77bo5$38bo63bo$39b2o59b2o$38b2o61b2o6$45bobo45bobo$46b2o45b2o$46bo47bo5$53bo33bo$54b2o29b2o$53b2o31b2o4$161bo$161bobo$60bobo15bobo69b2o12b2o6b2o$61b2o15b2o70b2o12b2o4bo3bo$61bo17bo84b2o3bo5bo8b2o$161bobo4b2obo3bo8b2o$161bo7bo5bo$170bo3bo$172b2o$162bo$160b2o$161b2o6$153bobo$153b2o$154bo5$147bo$145b2o$146b2o6$138bobo$138b2o$139bo5$132bo$130b2o$131b2o6$123bobo$123b2o$124bo5$117bo$115b2o$116b2o6$108bobo$108b2o$109bo5$102bo$100b2o$101b2o6$93bobo$93b2o$94bo5$89b2o49b2o$89bo51bo$87bobo51bobo$87b2o53b2o!", 0, false);
        add(list, "transistor_and_off", Category.CIRCUITS, "Транзистор «И» — Закрыт (A=0, B=1 → Линия=0)", "AND Transistor (OFF: A=0, B=1 -> Out=0)",
                "Вход A перекрыт заслонкой: внутренний инвертор сбивает каждый сигнал линии B", "Input A blocked by Eater: internal inverter cuts off signal line B completely",
                "x = 186, y = 133, rule = B3/S23\n24bo91bo$22bobo91bobo$12b2o6b2o12b2o69b2o12b2o6b2o$11bo3bo4b2o12b2o69b2o12b2o4bo3bo$2o8bo5bo3b2o97b2o3bo5bo8b2o$2o8bo3bob2o4bobo91bobo4b2obo3bo8b2o$10bo5bo7bo91bo7bo5bo$11bo3bo109bo3bo$12b2o113b2o$23bo91bo$24b2o89b3o$23b2o$113b2o$113b2o2$113b2o$113bo$30bobo78bobo$31b2o78b2o$31bo5$38bo$39b2o$38b2o6$45bobo$46b2o$46bo5$53bo$54b2o$53b2o4$161bo$161bobo$60bobo87b2o12b2o6b2o$61b2o87b2o12b2o4bo3bo$61bo102b2o3bo5bo8b2o$161bobo4b2obo3bo8b2o$161bo7bo5bo$170bo3bo$172b2o$68bo93bo$69b2o89b2o$68b2o91b2o6$75bobo75bobo$76b2o75b2o$76bo77bo5$83bo63bo$84b2o59b2o$83b2o61b2o6$90bobo45bobo$91b2o45b2o$91bo47bo5$98bo33bo$99b2o29b2o$98b2o31b2o6$105bobo15bobo$106b2o15b2o$106bo17bo5$117bo$115b2o$116b2o6$108bobo$108b2o$109bo5$102bo$100b2o$101b2o6$93bobo$93b2o$94bo5$89b2o49b2o$89bo51bo$87bobo51bobo$87b2o53b2o!", 0, false);
        add(list, "gate_or_on", Category.CIRCUITS, "Логический вентиль «ИЛИ» (A=1, B=0 → Линия=1)", "OR Logic Gate (A=1, B=0 -> Out=1)",
                "4-пушечная схема NOR + инвертор: формирует выходную линию сигналов при A или B", "4-gun pure Conway OR gate (NOR + inverter) driving an output signal line",
                "x = 231, y = 178, rule = B3/S23\n24bo91bo$22bobo91bobo$12b2o6b2o12b2o69b2o12b2o6b2o$11bo3bo4b2o12b2o69b2o12b2o4bo3bo$2o8bo5bo3b2o97b2o3bo5bo8b2o$2o8bo3bob2o4bobo91bobo4b2obo3bo8b2o$10bo5bo7bo91bo7bo5bo$11bo3bo109bo3bo$12b2o113b2o$23bo93bo$24b2o89b2o$23b2o91b2o6$30bobo75bobo$31b2o75b2o$31bo77bo5$38bo63bo$39b2o59b2o$38b2o61b2o6$45bobo45bobo$46b2o45b2o$46bo47bo5$53bo33bo$54b2o29b2o$53b2o31b2o4$161bo$161bobo$60bobo15bobo69b2o12b2o6b2o$61b2o15b2o70b2o12b2o4bo3bo$61bo17bo84b2o3bo5bo8b2o$161bobo4b2obo3bo8b2o$161bo7bo5bo$170bo3bo$172b2o$160bo$160b3o2$158b2o$158b2o2$158b2o$158bo$156bobo$156b2o27$206bo$206bobo$195b2o12b2o6b2o$195b2o12b2o4bo3bo$209b2o3bo5bo8b2o$206bobo4b2obo3bo8b2o$206bo7bo5bo$215bo3bo$217b2o$207bo$205b2o$206b2o6$198bobo$198b2o$199bo5$192bo$190b2o$191b2o2$100b2o$100bo$98bobo$98b2o$183bobo$183b2o$184bo5$177bo$175b2o$176b2o6$168bobo$168b2o$169bo5$162bo$160b2o$161b2o6$153bobo$153b2o$154bo5$147bo$145b2o$146b2o6$138bobo$138b2o$139bo5$134b2o49b2o$134bo51bo$132bobo51bobo$132b2o53b2o!", 0, false);
        add(list, "snark_corner_90", Category.CIRCUITS, "Поворот линии на 90° (Зеркало Snark)", "90-Deg Signal Corner (Simkin + Snark)",
                "Сигнальная линия бьёт в неподвижное зеркало Snark и поворачивает на 90°!", "P120 signal line hits a static Snark catalyst and turns cleanly 90 degrees!",
                "x = 161, y = 99, rule = B3/S23\n9bo$9b3o$12bo$11b2o4$6bo$2o3bo3b3o148bo$2o2bo6bo146b3o$5bo5bo145bo$6b2ob2o146b2o5$27b2o$27b2o2$24b2o5b2o$24b2o5b2o$18bo$16bobo$17b2o8$136b2o$135bobo$137bo18$48bo$46bobo$47b2o8$106b2o$105bobo$107bo13$84bo$82b3o$81bo$81b2o2$78bo$76bobo$77b2o3$71b2o$70bobo5b2o$70bo7b2o$69b2o2$83bo$79b2obobo$78bobobobo$75bo2bobobob2o$75b4ob2o2bo$79bo4bo$75b2o2bob3o$75b2o3b2o!", 0, false);
        add(list, "stargate_p60", Category.CIRCUITS, "Туннель сигналов «Звёздные врата» (P60)", "Star Gate Signal Tunnel (P60)",
                "Синхронные потоки глайдеров телепортируют космические корабли LWSS быстрее c/2!", "Glider signal streams create a tunnel propelling LWSS at apparent speed > c!",
                "x = 155, y = 133, rule = B3/S23\n25b2o85b2o$24bobo44bobo37bobo$10bo12b3o4b2o34bo4bo2bo35bo13bobo$5bo4b4o8b3o4bo2b4o31b2o5b2o8bo16b2o7bo2bo6b2o2bo2bo$5bo5b4o8b3o4b2ob3o26b2o8bo3b2o5bobo15b2o7bo8bobo5b2o$2o9bo2bo9bobo35b2o10b2o6bob2o25bobo3bo3bo3bo3b2o4b2o$2o9b4o10b2o44bo2bo6b2ob2o10b2o14b2o2b2ob3o5b2o6b2o$10b4o8bo48bobo8bob2o10b2o21b2o3bo2bo$10bo12bo59bobo20b2o16bobo$21b3o50bo9bo21bo$34b2o19b2o16bo21b2o7bobo$5b2o28bo19bo17b3o19b3o6b2o4bo$5b2o28bobo6b2o7bobo25bo15b2obo9bobo$36b3o5bobo6b2o24bobo4b3o8bo2bo9b2o$38b3o6bo30bobo16b2obo$5bo32bo2bo2bo2bo29bo2bo7bo2b2o2b3o$4bobo32b2o6bo8bo21bobo7bo3bo2b2o$3bo3bo8b3o25bobo9b3o7bo7b2o3bobo6bo2bo29b2o23b2o$3b5o10bo25b2o13bo6bobo4bobo5bo8b2o11bo17bobo22bo$2b2o3b2o8bo40b2o6b2o5bo28bo13b2o6bo11bobo5bobo$3b5o13bo30b2o18b2o28b3o7b2obo2bo2bo2bo10bo2bo5b2o$4b3o12b3o31bo58b2o2b2o6bo9b2o12b2o$5bo12bo18bo6bo8bobo65bobo8b2o3bo10b2o$18b2o18bo6b2o7b2o21b2o42b2o11b2o12b2o$9b2o25b3o5b2o15b2o13b4o11b2o33bo8bo2bo10bo$8bobo4bo12b2o30b2ob2o10b2ob2o10b2ob2o29b2o10bobo9bobo$10bo4bo11bobo31b4o11b2o13b4o21b2o7b2o21bobo$29bo32b2o28b2o22bo32bo$106bo7bobo$13b2o3b2o79b2obo3b4o4b2o$14b5o4b2o68b2o2b2obobo4b4o35b2o3b2o$15b3o4bo2bo66bobo2b2obob2o3bo2bo35bobobobo$16bo49bo24b3o8b2o3b4o6bobo27b5o$6b3o11bo43bobo15b2o6b3o8b2o3b4o7b2o29b3o$5bo3bo12bo42b2o15b2o7b3o12bo11bo30bo$21bo70bobo$4bo5bo82b2o$4b2o3b2o41bo6bo83b2o$30b3o20bo6b2o81bo$30bo20b3o5b2o50bo21bobo5bobo$7bo23bo77b2o22bo3bo3b2o$6bobo8b3o90b2o5bo19bo8bobo$6bobo106b4o14bo4bo7b2o$6bo10bobo26bobo65bobob2o4bo12bo9bo$6bo9b5o17b3o5bo3bo62bo2bob3o5b2obo3bo3bo$6bo2bo5b2o3b2o11b2o2bo2bobo7bo42bo19b2obob2o5b4obo2bobo$7b2o6b2o3b2o9bo2bo2b2o7bo4bo41b3o15b3ob4o6b2o2b2o19b3o$30bo19bo30bo14bo5bobo5bobo4bo31bo3bo$30bo10b3o2bo3bo3b2o23bobo13b2o5b2o6bo29bo7bo5bo$18bo11bo15bobo5bobo23b2o7b2o12bo5b2o27b2o9bo3bo$16b2o6b2o5bo2bo21bo33bo48b2o9b3o$23bobo7b2o21b2o32bobo57b3o$15bo7bo43bo6bo16b2o4bo2bo26bo2bo$22b2o44bo6b2o19bo14b4o11bo$16bo2bo46b3o5b2o20bo3bo10bo3bo10bo3bo$18b2o76b4o11bo14b4o20b2o$112bo2bo34b2o$135b2o8bo$135bo2bo6bobo$130b2o2bo3bo7bobo4b2o$129b3o2b2o2bo7bo2bo3b2o$119b2o5bob2o16bobo$96bo5bobo14b2o5bo2bo8b3o4bobo$94bobo6b2o21bob2o15bo$95b2o6bo25b3o$130b2o2$82bo6bo$83bo6b2o$17b2o28b2o28b2o2b3o5b2o$15b2ob2o25b2ob2o25b2ob2o$15b4o26b4o26b4o9bo$16b2o28b2o28b2o9b2o$87bobo2$128b2o$128b2o$112b2o26b2o$111b2o27b2o$88bo24bo$88b3o$91bo51bo$90b2o36b3o11bo$142bo$128bobo$127b5o$103bo22b2o3b2o5b2o3b2o$93bo8b2o22b2o3b2o8bo$92b3o7bobo33bo5bo$91b5o43b2ob2o$90bobobobo43bobo$90b2o3b2o44bo$127b2o12bo$126b2o$93bo31bo2bo2b2o$92bobo32bo3bo$91b2obo37b3o$91b3o40bo$90bo2bo43bobo$90b3o44b2o$89bo3bo44bo3b3o$88bo5bo23bo22bo3bo$89bo3bo5bo17b2o21bo5bo$90b3o7bo16bobo20b2obob2o$98b3o$131bo$129b2o$130b2o$111b2o$110bobo32b2o$107bo4bo12b2o18bo$88b2o15bobo17b2o19b3o$89bo16b2o40bo$86b3o26b2o21b2o$86bo29bo21bo2bo$92b2o22bobo9bo3b3o7bo$90bo3bo5bo2b3o11b2o8b5o3bo6bo$89bo5bo9bo3bo16b2ob2o3bo7bo$88bo2bo3bo8bo4bobo13b3ob2o3bo3bo2bo5b2o$95bo16b2o12b2ob4o5b2o7bobo$86bo3bo3bo17b2o13b4o18bo$85bob2o3b2o18b2o14bo20b2o$85bo23bobo4b2o$84b2o23bo6bobo$118bo$118b2o4$17b2o28b2o28b2o$15b2ob2o25b2ob2o25b2ob2o$15b4o26b4o26b4o$16b2o28b2o28b2o!", 0, false);
        add(list, "p30_racetrack", Category.CIRCUITS, "Кольцевой сигнальный трек (P1800)", "Glider Racetrack Signal Loop (P1800)",
                "Замкнутая трасса с инвертором пушки, синтезом LWSS и обратным преобразователем", "Period-1800 signal loop featuring an in-line glider-gun NOT gate!",
                "x = 308, y = 176, rule = B3/S23\n161b2o$162bo$162bobo7bo$163b2o5bobo$168b2o71b3o$168b2o12b2o56bo3bo$168b2o12b2o55bo5bo$170bobo$172bo65bo7bo$238bo7bo2$239bo5bo$240bo3bo$241b3o4$84bo$83bobo$66b2o15b2obo$66bobo14b2ob2o3b2o$61b2o6bo13b2obo4b2o$57b2obo2bo2bo2bo13bobo$57b2o2b2o6bo8bo5bo$66bobo7bobo$66b2o9b2o5$85bo$86bo$84b3o40b2o$127bobo8bo$118b3ob2o4b3o6bo7bo$118b4o2bo4b3o5b2o5bobo$122b2o4b3o7bo4bo3b2o3b2o$127bobo13bo3b2o3b2o$93bo33b2o14bo3b2o$91bobo50bobo$92b2o51bo$268bo$267bobo$266bob2o15b2o$131bo117b2o9b2o3b2ob2o14bobo$100bo30b2o112b4o4b2o5b2o4bob2o13bo6b2o$101bo28bobo112b3ob2o2b2o12bobo13bo2bo2bo2bob2o$99b3o148bo17bo5bo8bo6b2o2b2o$274bobo7bobo$274b2o9b2o$175bo2bo56bo2bo26bo2bo$174bo59bo29bo$123b2o49bo3bo55bo3bo25bo3bo$108bo15b2o48b4o56b4o26b4o$106bobo14bo155bo$107b2o169b2o$278bobo$159b2o2b3o2b2o81b2o7b2o$159bo2b5o2bo81bo9bo9bo$116bo43b9o83b9o10b2o$116b2o5bo33b3o9b3o77b3o2b5o2b3o6bobo$115bobo5bobo31bo2bo7bo2bo77bo2bo2b3o2bo2bo$126b2o4b2o24b2o9b2o79b2o9b2o23b2o9b2o$126b2o4b2o151b2o9bobo$126b2o152bo6bo7b3o4b2ob3o$113b2o8bobo126bo26bobo12b3o4bo2b4o$112bobo8bo126b4o6b2o2b2o11b2o3bo12b3o4b2o$112bo131b2o3bobob2o5b4obo2bobo6b2o3bo13bobo$111b2o131b2o2bo2bob3o5b2obo3bo3bo4b2o3bo14b2o$249bobob2o4bo12bo6bobo$250b4o14bo4bo6bo$252bo19bo$268bo3bo$268bobo11$20b2o$20b2o4$19b3o$19b3o$18bo3bo$17bo5bo142bo$18bo3bo101bobo39bobo$19b3o101bo2bo39b2o$114b2o6b2o10bo6bo$114b2o4b2o3bo8bo5bobo$122b2o9bo6b2obo$123bo2bo2b2o9b2ob2o3b2o$124bobo2bo2b3o5b2obo4b2o$107b2o20b4o7bobo$109bo20b2o9bo$96b2o12bo8b2o$17b2o77b2o4bo7bo8b2o$18bo74b2o5b2o8bo$15b3o67b2o5b3o5bo2b2o4bo13bo$15bo69b2o6b2o6b5ob2o12b2o$96b2o4bo19b2o$96b2o2$110bo$111bo$109b3o$114bobo$114b2o$115bo3$64bo53bo$62b3o51bobo$61bo55b2o$61b2o49bo$112b3o$115bo$114b2o2$117b2o$116b2o$118bo3$50b2o$51b2o$50bo74bo$124b2o$124bobo6b2o$133bo$134b3o$136bo$99bo$34b2o61b3o$34bo61bo35b2o$25bo6bobo61b2o33b2o$22b4o6b2o99bo$13bo7b4o68b2o$12bobo6bo2bo67bobo50b2o$11bo3b2o4b4o10b2o57bo50bo2bo$2o9bo3b2o5b4o10b2o97bo3b3o7bo6b2o$2o9bo3b2o8bo9bo98b5o3bo6bo6b2o$12bobo107b2o9b2ob2o3bo7bo$13bo108b2o8b3ob2o3bo3bo2bo$22bobo108b2ob4o5b2o$23b2o60b3o46b4o$23bo63bo47bo$86bo$27b2o$27b2o2$14b2o$13b3o3b2obo55b2o$3b2o5bob2o5bo3bo2bo50bobo$3b2o5bo2bo4bo4bo2b2o51bo$10bob2o4b4o5b2o8b2o$13b3o3bo7b3o7b2o$14b2o11b2o$26b2o$26bo$59b2o9b3o$51bo7bo2bo9bo$50bo3b2o7bo7bo5bo$50bo5bo6bo12b4o$51b5o7bo11b2obobo3b2o$59bo2bo11b3obo2bo2b2o$59b2o14b2obobo$76b4o$77bo!", 0, false);

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
