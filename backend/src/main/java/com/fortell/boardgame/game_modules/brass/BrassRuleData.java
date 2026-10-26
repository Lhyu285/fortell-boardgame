package com.fortell.boardgame.game_modules.brass;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.asLong;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.listOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.objectList;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseInteger;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.parseStaticInteger;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.staticListOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.staticMapOf;
import static com.fortell.boardgame.game_modules.brass.BrassJsonCodec.staticObjectList;

/** Rule Data rules; all working state is passed explicitly. */
final class BrassRuleData {
    private BrassRuleData() {}

    static final int STARTING_MONEY = 17;
    static final int STARTING_INCOME_LEVEL = 10;
    static final int MAX_INCOME_LEVEL = 99;
    static final int STARTING_HAND_SIZE = 8;
    static final int MAX_PLAYER_LINKS = 14;
    static final int MAX_COAL_MARKET_SIZE = 14;
    static final int MAX_IRON_MARKET_SIZE = 10;
    static final int DISTANT_COAL_PRICE = 8;
    static final int DISTANT_IRON_PRICE = 6;
    static final int MAX_NOTICE_COUNT = 16;
    static final List<Integer> FULL_COAL_MARKET = List.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7);
    static final List<Integer> INITIAL_COAL_MARKET = List.of(1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7);
    static final List<Integer> FULL_IRON_MARKET = List.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5);
    static final List<Integer> INITIAL_IRON_MARKET = List.of(2, 2, 3, 3, 4, 4, 5, 5);
    static final List<String> PLAYER_COLORS = List.of("red", "yellow", "blue", "purple");

    static final List<String> CITY_CARDS = List.of(
            "Warrington", "Stoke-on-Trent", "Stone", "Stafford", "Cannock", "Wolverhampton",
            "Coalbrookdale", "Dudley", "Kidderminster", "Worcester", "Birmingham", "Coventry",
            "Nuneaton", "Tamworth", "Walsall", "Burton-on-Trent", "Derby", "Nottingham",
            "Leek", "Belper", "Redditch", "Gloucester", "Oxford", "Shrewsbury"
    );

    static final List<String> INDUSTRY_TYPES = List.of(
            "cotton_mill", "manufacturer", "brewery", "pottery", "iron_works", "coal_mine"
    );
    static final List<String> SELLABLE_INDUSTRIES = List.of("cotton_mill", "manufacturer", "pottery");
    static final List<String> CANAL_ERA_REMOVED_LEVEL_ONE_INDUSTRIES = List.of(
            "coal_mine", "iron_works", "cotton_mill", "manufacturer", "brewery", "pottery"
    );

    static final Map<String, String> INDUSTRY_NAMES = Map.of(
            "cotton_mill", "\u68c9\u7eba\u5382",
            "manufacturer", "\u52a0\u5de5\u5382",
            "brewery", "\u917f\u9152\u5382",
            "pottery", "\u9676\u74f7\u5382",
            "iron_works", "\u94c1\u5382",
            "coal_mine", "\u7164\u77ff"
    );
    static final Map<String, Integer> INDUSTRY_COSTS = Map.of(
            "cotton_mill", 12,
            "manufacturer", 8,
            "brewery", 5,
            "pottery", 17,
            "iron_works", 5,
            "coal_mine", 5
    );
    static final Map<String, Integer> INDUSTRY_INCOME_REWARDS = Map.of(
            "cotton_mill", 5,
            "manufacturer", 3,
            "brewery", 4,
            "pottery", 5,
            "iron_works", 3,
            "coal_mine", 4
    );
    static final Map<String, Integer> INDUSTRY_VP_REWARDS = Map.of(
            "cotton_mill", 5,
            "manufacturer", 3,
            "brewery", 4,
            "pottery", 10,
            "iron_works", 3,
            "coal_mine", 3
    );
    static final Map<String, List<String>> CITY_SLOTS = Map.ofEntries(
            Map.entry("Warrington", List.of("cotton_mill", "manufacturer", "brewery")),
            Map.entry("Stoke-on-Trent", List.of("pottery", "coal_mine", "iron_works")),
            Map.entry("Stone", List.of("coal_mine", "brewery")),
            Map.entry("Stafford", List.of("pottery", "manufacturer")),
            Map.entry("Cannock", List.of("coal_mine", "iron_works")),
            Map.entry("Wolverhampton", List.of("manufacturer", "iron_works")),
            Map.entry("Coalbrookdale", List.of("coal_mine", "iron_works")),
            Map.entry("Dudley", List.of("coal_mine", "iron_works")),
            Map.entry("Kidderminster", List.of("cotton_mill", "manufacturer")),
            Map.entry("Worcester", List.of("cotton_mill", "brewery")),
            Map.entry("Birmingham", List.of("cotton_mill", "manufacturer", "iron_works", "brewery")),
            Map.entry("Coventry", List.of("manufacturer", "pottery")),
            Map.entry("Nuneaton", List.of("cotton_mill", "coal_mine")),
            Map.entry("Tamworth", List.of("cotton_mill", "manufacturer")),
            Map.entry("Walsall", List.of("manufacturer", "iron_works")),
            Map.entry("Burton-on-Trent", List.of("brewery", "manufacturer")),
            Map.entry("Derby", List.of("cotton_mill", "manufacturer", "iron_works")),
            Map.entry("Nottingham", List.of("cotton_mill", "manufacturer", "brewery")),
            Map.entry("Leek", List.of("cotton_mill", "manufacturer")),
            Map.entry("Belper", List.of("cotton_mill", "coal_mine", "iron_works")),
            Map.entry("Redditch", List.of("manufacturer", "iron_works")),
            Map.entry("Gloucester", List.of("brewery", "manufacturer")),
            Map.entry("Oxford", List.of("cotton_mill", "manufacturer")),
            Map.entry("Shrewsbury", List.of("cotton_mill", "manufacturer"))
    );
    static final List<Map<String, Object>> ROUTES = BrassJsonCodec.immutableData(List.of(
            route("Warrington", "Wigan", false, true),
            route("Warrington", "Stoke-on-Trent", true, true),
            route("Stoke-on-Trent", "Stone", true, true),
            route("Stone", "Stafford", true, true),
            route("Stafford", "Cannock", true, true),
            route("Cannock", "Wolverhampton", true, true),
            route("Wolverhampton", "Birmingham", true, true),
            route("Birmingham", "Walsall", true, true),
            route("Birmingham", "Coventry", true, true),
            route("Birmingham", "Kidderminster", true, true),
            route("Birmingham", "Redditch", true, true),
            route("Coventry", "Nuneaton", true, true),
            route("Nuneaton", "Tamworth", true, true),
            route("Tamworth", "Burton-on-Trent", true, true),
            route("Burton-on-Trent", "Derby", true, true),
            route("Derby", "Nottingham", true, true),
            route("Derby", "Belper", true, true),
            route("Belper", "Leek", true, true),
            route("Dudley", "Walsall", true, true),
            route("Dudley", "Coalbrookdale", true, true),
            route("Coalbrookdale", "Wolverhampton", true, true),
            route("Kidderminster", "Worcester", true, true),
            route("Worcester", "Gloucester", true, true),
            route("Redditch", "Oxford", true, true),
            route("Gloucester", "Oxford", true, true),
            route("Shrewsbury", "Coalbrookdale", true, true)
    ));
    static final Map<String, Object> BIRMINGHAM_MAP = BrassJsonCodec.immutableData(loadBirminghamMap());
    static final Map<String, String> CITY_NAME_MAPPING = BrassJsonCodec.immutableData(loadCityNameMapping());
    static final Map<String, Map<String, Object>> MAP_CITY_METADATA = BrassJsonCodec.immutableData(loadCityMetadata(BIRMINGHAM_MAP));
    static final Map<String, List<List<String>>> MAP_CITY_SLOT_OPTIONS = BrassJsonCodec.immutableData(loadCitySlotOptions(BIRMINGHAM_MAP));
    static final List<Map<String, Object>> MAP_ROUTES = BrassJsonCodec.immutableData(loadRoutes(BIRMINGHAM_MAP));
    static final List<String> MAP_CITY_CARDS = BrassJsonCodec.immutableData(loadCityCards(BIRMINGHAM_MAP));
    static final List<Map<String, Object>> MAP_MARKETS = BrassJsonCodec.immutableData(loadMarkets(BIRMINGHAM_MAP));
    static final List<Integer> INCOME_BY_LEVEL = BrassJsonCodec.immutableData(loadIncomeConfig());
    static final Map<Integer, Integer> INCOME_LEVEL_AFTER_LOAN = BrassJsonCodec.immutableData(loadIncomeLevelAfterLoan());
    static final Map<String, List<Map<String, Object>>> BUILDING_TILES = BrassJsonCodec.immutableData(loadBuildingConfig());
    static final Map<String, Object> CARD_CONFIG = BrassJsonCodec.immutableData(loadJsonObject("Birmingham_cards.json"));
    static final List<Map<String, Object>> MERCHANT_TILE_CONFIG = BrassJsonCodec.immutableData(loadJsonList("Birmingham_merchant_tiles.json"));

    static Map<String, Object> mapSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("loadedFromFile", !BIRMINGHAM_MAP.isEmpty());
        summary.put("markets", MAP_MARKETS.isEmpty() ? fallbackMarkets() : MAP_MARKETS);
        summary.put("cityNames", CITY_NAME_MAPPING);
        summary.put("industryNames", industryNameSummary());
        summary.put("cities", new ArrayList<>(MAP_CITY_METADATA.values()));
        return summary;
    }

    static Map<String, String> industryNameSummary() {
        Map<String, String> names = new LinkedHashMap<>();
        for (String industryType : INDUSTRY_TYPES) {
            names.put(industryType, industryNameCn(industryType));
        }
        return names;
    }

    static int incomeForLevel(int incomeLevel) {
        if (INCOME_BY_LEVEL.isEmpty()) {
            return incomeLevel < 0 ? incomeLevel : incomeLevel / 5;
        }
        int index = Math.max(0, Math.min(incomeLevel, INCOME_BY_LEVEL.size() - 1));
        return INCOME_BY_LEVEL.get(index);
    }

    static String eraName(String era) {
        return "rail".equals(era) ? "\u94c1\u8def\u65f6\u4ee3" : "\u8fd0\u6cb3\u65f6\u4ee3";
    }

    static String eraLinkType(String era) {
        return "rail".equals(era) ? "rail" : "canal";
    }

    static String industryNameCn(String industryType) {
        return switch (industryType) {
            case "cotton_mill" -> CITY_NAME_MAPPING.getOrDefault("Cotton", "\u68c9\u7eba\u5382");
            case "manufacturer" -> CITY_NAME_MAPPING.getOrDefault("Manufacture", "\u52a0\u5de5\u5382");
            case "brewery" -> CITY_NAME_MAPPING.getOrDefault("Brewery", "\u917f\u9152\u5382");
            case "pottery" -> CITY_NAME_MAPPING.getOrDefault("Pottery", "\u9676\u74f7\u5382");
            case "iron_works" -> CITY_NAME_MAPPING.getOrDefault("Iron", "\u94a2\u94c1\u5382");
            case "coal_mine" -> CITY_NAME_MAPPING.getOrDefault("Coal", "\u7164\u77ff\u573a");
            default -> INDUSTRY_NAMES.getOrDefault(industryType, industryType);
        };
    }

    static int amountForPlayerCount(Object rawAmounts, int playerCount) {
        Map<String, Object> amounts = staticMapOf(rawAmounts);
        if (amounts.isEmpty()) {
            return 0;
        }
        String key = String.valueOf(Math.max(2, Math.min(4, playerCount)));
        return parseInteger(amounts.get(key), 0);
    }

    static Map<String, Object> route(String from, String to, boolean canal, boolean rail) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("from", from);
        route.put("to", to);
        route.put("canal", canal);
        route.put("rail", rail);
        return route;
    }

    static Map<String, Object> loadBirminghamMap() {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths("Birmingham_map.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            } catch (IOException ignored) {
                return new LinkedHashMap<>();
            }
        }
        try (InputStream input = BrassRuleData.class.getResourceAsStream("Birmingham_map.json")) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            }
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    static Map<String, String> loadCityNameMapping() {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths("Birmingham_city.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<Map<String, String>>() {});
            } catch (IOException ignored) {
                return new LinkedHashMap<>();
            }
        }
        try (InputStream input = BrassRuleData.class.getResourceAsStream("Birmingham_city.json")) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<Map<String, String>>() {});
            }
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    static Map<String, List<List<String>>> loadCitySlotOptions(Map<String, Object> map) {
        Map<String, List<List<String>>> slotsByCity = new LinkedHashMap<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            if (!"Industry".equals(Objects.toString(city.get("type"), ""))) {
                continue;
            }
            String cityName = displayCityName(Objects.toString(city.get("name"), ""));
            List<List<String>> slots = new ArrayList<>();
            Map<String, Object> detail = staticMapOf(city.get("detail"));
            for (Object rawSlot : staticObjectList(detail.get("factory_type"))) {
                List<String> options = new ArrayList<>();
                for (Object rawIndustry : staticObjectList(rawSlot)) {
                    String industryType = normalizeIndustryType(Objects.toString(rawIndustry, ""));
                    if (!industryType.isBlank()) {
                        options.add(industryType);
                    }
                }
                if (!options.isEmpty()) {
                    slots.add(options.stream().distinct().toList());
                }
            }
            if (!slots.isEmpty()) {
                slotsByCity.put(cityName, slots);
            }
        }
        return slotsByCity;
    }

    static List<Map<String, Object>> loadRoutes(Map<String, Object> map) {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Map<String, Object> road : staticListOf(map.get("road"))) {
            List<String> types = staticObjectList(road.get("type")).stream()
                    .map(String::valueOf)
                    .toList();
            Map<String, Object> route = route(
                    displayCityName(Objects.toString(road.get("src"), "")),
                    displayCityName(Objects.toString(road.get("dst"), "")),
                    types.contains("canal"),
                    types.contains("railway") || types.contains("rail")
            );
            route.put("hasBrewery", Boolean.TRUE.equals(road.get("hasBrewery")));
            routes.add(route);
        }
        return routes;
    }

    static List<String> loadCityCards(Map<String, Object> map) {
        List<String> cities = new ArrayList<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            String name = displayCityName(Objects.toString(city.get("name"), ""));
            if (!List.of("Personal_Brewery", "Rural_Brewery").contains(name) && !cities.contains(name)) {
                cities.add(name);
            }
        }
        return cities;
    }

    static List<Map<String, Object>> loadMarkets(Map<String, Object> map) {
        List<Map<String, Object>> markets = new ArrayList<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            if (!"Market".equals(Objects.toString(city.get("type"), ""))) {
                continue;
            }
            Map<String, Object> detail = staticMapOf(city.get("detail"));
            Map<String, Object> market = new LinkedHashMap<>();
            market.put("city", displayCityName(Objects.toString(city.get("name"), "")));
            market.put("availablePlayers", parseStaticInteger(detail.get("available_players"), 0));
            market.put("roadPoint", parseStaticInteger(detail.get("road_point"), 0));
            market.put("marketCount", parseStaticInteger(detail.get("market_num"), 1));
            market.put("reward", Objects.toString(detail.get("sale_reward"), ""));
            markets.add(market);
        }
        return markets;
    }

    static List<Integer> loadIncomeConfig() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> config = new LinkedHashMap<>();
        for (Path path : brassSourcePaths("Income_config.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                break;
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }
        if (config.isEmpty()) {
            try (InputStream input = BrassRuleData.class.getResourceAsStream("Income_config.json")) {
                if (input != null) {
                    config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                }
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }

        List<Integer> incomeByLevel = new ArrayList<>();
        for (Object rawValue : staticObjectList(config.get("incomeByLevel"))) {
            incomeByLevel.add(parseStaticInteger(rawValue, 0));
        }
        return incomeByLevel;
    }

    static Map<Integer, Integer> loadIncomeLevelAfterLoan() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : loadJsonObject("IncomeLevel_after_Loan.json").entrySet()) {
            try {
                result.put(Integer.parseInt(entry.getKey()), parseStaticInteger(entry.getValue(), 0));
            } catch (NumberFormatException ignored) {
                // Ignore malformed configuration keys.
            }
        }
        return result;
    }

    static Map<String, List<Map<String, Object>>> loadBuildingConfig() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> config = new LinkedHashMap<>();
        for (Path path : brassSourcePaths("Birmingham_buildings.json")) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                break;
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }
        if (config.isEmpty()) {
            try (InputStream input = BrassRuleData.class.getResourceAsStream("Birmingham_buildings.json")) {
                if (input != null) {
                    config = mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                }
            } catch (IOException ignored) {
                config = new LinkedHashMap<>();
            }
        }

        Map<String, List<Map<String, Object>>> buildings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String industryType = normalizeIndustryType(entry.getKey());
            if (industryType.isBlank()) {
                continue;
            }
            List<Map<String, Object>> tiles = new ArrayList<>();
            int copyIndex = 1;
            for (Map<String, Object> rawTile : staticListOf(entry.getValue())) {
                int amount = Math.max(1, parseStaticInteger(rawTile.get("amount"), 1));
                for (int copy = 1; copy <= amount; copy++) {
                    tiles.add(buildingTile(industryType, rawTile, copyIndex));
                    copyIndex++;
                }
            }
            if (!tiles.isEmpty()) {
                buildings.put(industryType, tiles);
            }
        }
        return buildings;
    }

    static Map<String, Object> loadJsonObject(String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths(fileName)) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            } catch (IOException ignored) {
                return new LinkedHashMap<>();
            }
        }
        try (InputStream input = BrassRuleData.class.getResourceAsStream(fileName)) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            }
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    static List<Map<String, Object>> loadJsonList(String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        for (Path path : brassSourcePaths(fileName)) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return mapper.readValue(input, new TypeReference<List<Map<String, Object>>>() {});
            } catch (IOException ignored) {
                return new ArrayList<>();
            }
        }
        try (InputStream input = BrassRuleData.class.getResourceAsStream(fileName)) {
            if (input != null) {
                return mapper.readValue(input, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (IOException ignored) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    static List<Path> brassSourcePaths(String fileName) {
        return List.of(
                Path.of("src/main/java/com/fortell/boardgame/game_modules/brass/" + fileName),
                Path.of("backend/src/main/java/com/fortell/boardgame/game_modules/brass/" + fileName)
        );
    }

    static Map<String, Object> buildingTile(String industryType, Map<String, Object> rawTile, int copyIndex) {
        int level = parseStaticInteger(rawTile.get("level"), 1);
        int period = parseStaticInteger(rawTile.get("period"), 0);
        List<Integer> resourceAmounts = resourceAmounts(rawTile.get("resource_amount"));
        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("id", industryType + "_level_" + level + "_" + copyIndex);
        tile.put("industryType", industryType);
        tile.put("industryName", INDUSTRY_NAMES.get(industryType));
        tile.put("level", level);
        tile.put("era", period == 1 ? "canal" : "rail");
        tile.put("period", period);
        tile.put("cost", parseStaticInteger(rawTile.get("price"), INDUSTRY_COSTS.get(industryType)));
        tile.put("coalCost", parseStaticInteger(rawTile.get("coal_cost"), 0));
        tile.put("ironCost", parseStaticInteger(rawTile.get("iron_cost"), 0));
        tile.put("saleBeerCost", parseStaticInteger(rawTile.get("sale_wine"), SELLABLE_INDUSTRIES.contains(industryType) ? 1 : 0));
        tile.put("resourceAmounts", resourceAmounts);
        tile.put("coal", 0);
        tile.put("iron", 0);
        tile.put("beer", 0);
        tile.put("incomeReward", parseStaticInteger(rawTile.get("incomeLevel_increase"), INDUSTRY_INCOME_REWARDS.get(industryType)));
        tile.put("victoryPoints", parseStaticInteger(rawTile.get("VP"), INDUSTRY_VP_REWARDS.get(industryType)));
        tile.put("roadPoints", parseStaticInteger(rawTile.get("road_point"), 1));
        tile.put("canDevelop", !Boolean.FALSE.equals(rawTile.get("can_develop")));
        tile.put("flipType", Objects.toString(rawTile.get("flip_type"), SELLABLE_INDUSTRIES.contains(industryType) ? "sell" : "deplete"));
        return tile;
    }

    static List<Integer> resourceAmounts(Object rawValue) {
        if (rawValue instanceof List<?> values) {
            int canal = values.isEmpty() ? 0 : parseStaticInteger(values.get(0), 0);
            int rail = values.size() < 2 ? canal : parseStaticInteger(values.get(1), canal);
            return List.of(canal, rail);
        }
        int amount = parseStaticInteger(rawValue, 0);
        return List.of(amount, amount);
    }

    static int resourceAmountForEra(Map<String, Object> boardTile, String era) {
        List<Object> amounts = objectList(boardTile.get("resourceAmounts"));
        if (amounts.isEmpty()) {
            String resourceField = switch (Objects.toString(boardTile.get("industryType"), "")) {
                case "coal_mine" -> "coal";
                case "iron_works" -> "iron";
                case "brewery" -> "beer";
                default -> "";
            };
            return resourceField.isBlank() ? 0 : parseInteger(boardTile.get(resourceField), 0);
        }
        int index = "rail".equals(era) ? 1 : 0;
        return parseInteger(amounts.get(Math.min(index, amounts.size() - 1)), 0);
    }

    static List<String> cityCards() {
        return MAP_CITY_CARDS.isEmpty() ? CITY_CARDS : MAP_CITY_CARDS;
    }

    static List<Map<String, Object>> routes() {
        return MAP_ROUTES.isEmpty() ? ROUTES : MAP_ROUTES;
    }

    static Map<String, List<List<String>>> citySlotOptions() {
        if (!MAP_CITY_SLOT_OPTIONS.isEmpty()) {
            return MAP_CITY_SLOT_OPTIONS;
        }
        Map<String, List<List<String>>> fallback = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : CITY_SLOTS.entrySet()) {
            fallback.put(entry.getKey(), entry.getValue().stream().map(List::of).toList());
        }
        return fallback;
    }

    static boolean cityCanBuild(String city, String industryType) {
        return citySlotOptions().getOrDefault(city, List.of()).stream()
                .anyMatch(slot -> slot.contains(industryType));
    }

    static List<Map<String, Object>> fallbackMarkets() {
        List<Map<String, Object>> markets = new ArrayList<>();
        markets.add(market("Warrington", 3, 2, "5英镑"));
        markets.add(market("Shrewsbury", 2, 1, "4 VP"));
        markets.add(market("Nottingham", 4, 2, "3 VP"));
        markets.add(market("Gloucester", 2, 2, "Develop 1"));
        markets.add(market("Oxford", 2, 2, "2 Income"));
        return markets;
    }

    static Map<String, Object> market(String city, int availablePlayers, int marketCount, String reward) {
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("city", city);
        market.put("availablePlayers", availablePlayers);
        market.put("roadPoint", 2);
        market.put("marketCount", marketCount);
        market.put("reward", reward);
        return market;
    }

    static String displayCityName(String raw) {
        if (raw.isBlank()) {
            return "";
        }
        for (String city : CITY_CARDS) {
            if (city.equalsIgnoreCase(raw)) {
                return city;
            }
        }
        if ("BREWERY".equalsIgnoreCase(raw)) {
            return "Personal_Brewery";
        }
        return raw;
    }

    static String normalizeIndustryType(String raw) {
        String value = raw == null ? "" : raw.trim();
        return switch (value.toLowerCase()) {
            case "cotton", "cotton_mill" -> "cotton_mill";
            case "manufacture", "manufacturer" -> "manufacturer";
            case "brewery" -> "brewery";
            case "pottery" -> "pottery";
            case "iron", "iron_works" -> "iron_works";
            case "coal", "coal_mine" -> "coal_mine";
            default -> "";
        };
    }

    static List<String> configuredIndustryTypes(Object rawFactory) {
        List<Object> factories;
        if (rawFactory instanceof List<?> list) {
            factories = new ArrayList<>(list);
        } else if (rawFactory == null) {
            factories = new ArrayList<>();
        } else {
            factories = new ArrayList<>(List.of(rawFactory));
        }
        if (factories.stream().anyMatch(factory -> "all".equalsIgnoreCase(Objects.toString(factory, "")))) {
            return new ArrayList<>(INDUSTRY_TYPES);
        }
        return factories.stream()
                .map(factory -> normalizeIndustryType(Objects.toString(factory, "")))
                .filter(industry -> !industry.isBlank())
                .distinct()
                .toList();
    }

    static Map<String, Map<String, Object>> loadCityMetadata(Map<String, Object> map) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> city : staticListOf(map.get("city"))) {
            String name = displayCityName(Objects.toString(city.get("name"), ""));
            if (name.isBlank()) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", name);
            meta.put("cnName", CITY_NAME_MAPPING.getOrDefault(name.toUpperCase(), Objects.toString(city.get("cn_name"), name)));
            meta.put("color", Objects.toString(city.get("color"), "white"));
            meta.put("type", Objects.toString(city.get("type"), ""));
            result.put(name, meta);
        }
        return result;
    }

    static String cityCnName(String city) {
        String raw = Objects.toString(city, "");
        String mapped = CITY_NAME_MAPPING.get(raw.toUpperCase());
        if (mapped != null) {
            return mapped;
        }
        return Objects.toString(MAP_CITY_METADATA.getOrDefault(raw, Map.of()).get("cnName"), raw);
    }

    static String playerColor(Map<String, Object> state, long playerId) {
        return listOf(state.get("players")).stream()
                .filter(player -> asLong(player.get("userId")) == playerId)
                .map(player -> Objects.toString(player.get("color"), ""))
                .filter(color -> !color.isBlank())
                .findFirst()
                .orElse("red");
    }

    static String playerColorLabel(String color) {
        return switch (color) {
            case "red" -> "\u7ea2\u8272";
            case "yellow" -> "\u9ec4\u8272";
            case "blue" -> "\u84dd\u8272";
            case "purple" -> "\u7d2b\u8272";
            default -> color;
        };
    }

    static String playerName(List<Map<String, Object>> players, long userId) {
        return players.stream()
                .filter(player -> asLong(player.get("userId")) == userId)
                .map(player -> Objects.toString(player.get("username"), ""))
                .findFirst()
                .orElse("");
    }
}
