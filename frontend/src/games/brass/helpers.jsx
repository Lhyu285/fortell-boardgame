import { TEXT } from "../../features/rooms/roomText.js";

const BRASS_CITIES = [
  "WARRINGTON", "STOKE-ON-TRENT", "STONE", "STAFFORD", "CANNOCK", "WOLVERHAMPTON",
  "COALBROOKDALE", "DUDLEY", "KIDDERMINSTER", "WORCESTER", "BIRMINGHAM", "COVENTRY",
  "NUNEATON", "TAMWORTH", "WALSALL", "BURTON-ON-TRENT", "DERBY", "NOTTINGHAM",
  "LEEK", "BELPER", "REDDITCH", "GLOUCESTER", "OXFORD", "SHREWSBURY"
];

const BRASS_INDUSTRIES = {
  cotton_mill: "棉纺厂",
  manufacturer: "加工厂",
  brewery: "酿酒厂",
  pottery: "陶瓷厂",
  iron_works: "钢铁厂",
  coal_mine: "煤矿场"
};

const BRASS_PLAYER_COLORS = {
  red: { label: "红色", css: "#b93a32" },
  yellow: { label: "黄色", css: "#d9a621" },
  blue: { label: "蓝色", css: "#2868b7" },
  purple: { label: "紫色", css: "#7b4bb3" }
};

const BRASS_CITY_CN = {
  WARRINGTON: "沃灵顿",
  SHREWSBURY: "舒兹伯利",
  NOTTINGHAM: "诺丁汉",
  GLOUCESTER: "格罗斯特",
  OXFORD: "牛津",
  LEEK: "利克",
  "STOKE-ON-TRENT": "斯托克",
  STONE: "斯通",
  UTTOXETER: "伍拓希特尔",
  BELPER: "贝柏",
  DERBY: "德比",
  STAFFORD: "斯坦福",
  "BURTON-ON-TRENT": "特伦河畔伯顿",
  CANNOCK: "坎诺克",
  TAMWORTH: "谭沃思",
  WALSALL: "沃尔索尔",
  WOLVERHAMPTON: "伍尔弗汉普顿",
  COALBROOKDALE: "柯尔布鲁德尔",
  DUDLEY: "达德利",
  KIDDERMINSTER: "基德明斯特",
  WORCESTER: "伍斯特",
  BIRMINGHAM: "伯明翰",
  NUNEATON: "纳尼顿",
  COVENTRY: "考文垂",
  REDDITCH: "雷迪奇",
  RURAL_BREWERY: "乡村酿酒厂",
  PERSONAL_BREWERY: "私人酿酒厂"
};

function buildBrassResourceRequirements({
  activeAction,
  state,
  selectedBuildOption,
  buildCoalSources,
  buildIronSources,
  networkNeedsCoal,
  secondNetworkNeedsCoal,
  selectedRoute,
  selectedSecondRoute,
  networkCoalSources,
  secondNetworkCoalSources,
  networkBeerSources,
  developNeedsIron,
  developAmount,
  developIronSources,
  selectedSellOption,
  sellBeerSources,
  sellMerchantSources,
  selectedMerchantId,
  cityLabel
}) {
  const market = state.market ?? {};
  const marketAmount = (type) => Array.isArray(market[type]) ? market[type].length : 0;
  const industrySources = (type, sources) => (sources ?? []).map((source) => ({
    id: String(source.id),
    sourceType: "industry",
    type,
    available: Number(source.amount ?? source[type] ?? 0),
    label: `${source.ownerName ?? ""} ${source.city ?? ""} ${source.industryName ?? ""}`.trim()
  }));
  const marketSource = (type, amount) => ({
    id: `market:${type}`,
    sourceType: "market",
    type,
    // 市场牌面不足时，旧规则仍允许按固定价格购买；界面因此把市场来源
    // 的可选上限按本次需求展示，实际价格和牌面扣除仍由后端结算。
    available: amount,
    label: `市场${type === "coal" ? "煤" : "铁"}`
  });
  const freeRequirement = (type, amount, sources, label) => {
    if (!amount) return null;
    const normalized = industrySources(type, sources);
    return {
      type,
      amount,
      label,
      sources: [...normalized, marketSource(type, amount)],
      freeAvailable: normalized.reduce((sum, source) => sum + source.available, 0),
      marketAvailable: marketAmount(type)
    };
  };
  if (activeAction === "build" && selectedBuildOption) {
    return [
      freeRequirement("coal", Number(selectedBuildOption.coalCost ?? 0), buildCoalSources,
        `建造${cityLabel(selectedBuildOption.city)}需要的煤`),
      freeRequirement("iron", Number(selectedBuildOption.ironCost ?? 0), buildIronSources,
        `建造${cityLabel(selectedBuildOption.city)}需要的铁`)
    ].filter(Boolean);
  }
  if (activeAction === "develop" && developNeedsIron) {
    return [freeRequirement("iron", Number(developAmount ?? 0), developIronSources,
      `研发${developAmount}个产业需要的铁`)].filter(Boolean);
  }
  if (activeAction === "network" && networkNeedsCoal) {
    const requirements = [freeRequirement("coal", 1, networkCoalSources,
      `修建${selectedRoute ? `${cityLabel(selectedRoute.from)}-${cityLabel(selectedRoute.to)}` : "第一条路线"}需要的煤`)];
    if (secondNetworkNeedsCoal) {
      requirements.push(freeRequirement("coal", 1, secondNetworkCoalSources,
        `修建${selectedSecondRoute ? `${cityLabel(selectedSecondRoute.from)}-${cityLabel(selectedSecondRoute.to)}` : "第二条路线"}需要的煤`));
      requirements.push({
        type: "beer",
        amount: 1,
        label: "同时修建两条铁路需要的酒",
        sources: industrySources("beer", networkBeerSources),
        freeAvailable: 0,
        marketAvailable: 0
      });
    }
    return requirements.filter(Boolean);
  }
  if (activeAction === "sell" && selectedSellOption) {
    const amount = Number(selectedSellOption.saleBeerCost ?? 0);
    if (!amount) return [];
    return [{
      type: "beer",
      amount,
      label: `出售${cityLabel(selectedSellOption.city)}产业需要的酒`,
      sources: [
        ...industrySources("beer", sellBeerSources),
        ...(sellMerchantSources ?? [])
          .filter((merchant) => !selectedMerchantId || String(merchant.id) === String(selectedMerchantId))
          .map((merchant) => ({
          id: String(merchant.id),
          sourceType: "merchant",
          type: "beer",
          available: Number(merchant.beer ?? 0),
          label: `${cityLabel(merchant.city)}贸易商酒`
          }))
      ],
      freeAvailable: 0,
      marketAvailable: 0
    }];
  }
  return [];
}

function resourceSourcesForSelect(actionSources, industries, industryType, resourceField) {
  if (Array.isArray(actionSources)) {
    return actionSources.map((source) => ({
      id: source.id,
      ownerName: source.ownerName,
      city: source.city,
      industryName: BRASS_INDUSTRIES[industryType] ?? industryType,
      [resourceField]: source.amount
    }));
  }
  return industries.filter((tile) => tile.industryType === industryType && (tile[resourceField] ?? 0) > 0);
}

function buildConfirmText(option = {}, industryNames = BRASS_INDUSTRIES, cityLabel = (city) => city) {
  const industryName = industryNames[option.industryType] ?? option.industryName ?? option.industryType ?? "-";
  const parts = [
    `是否在${cityLabel(option.city)}城市建造${option.level ? `${option.level}级` : ""}${industryName}？`,
    `花费：${option.cost ?? 0}英镑`
  ];
  const resourceCosts = [];
  if (Number(option.coalCost ?? 0) > 0) resourceCosts.push(`煤x${option.coalCost}`);
  if (Number(option.ironCost ?? 0) > 0) resourceCosts.push(`铁x${option.ironCost}`);
  parts.push(`资源需求：${resourceCosts.length ? resourceCosts.join("、") : "无"}`);
  if (option.coversOpponent) {
    parts.push("该建造会覆盖对手资源产业。");
  }
  return parts.join("\n");
}

function sellConfirmText(option = {}, merchant = null, cityLabel = (city) => city) {
  const industryName = brassIndustryName(option.industryType, option.industryName);
  const parts = [
    `是否出售${cityLabel(option.city)}城市的${option.level ? `${option.level}级` : ""}${industryName}？`
  ];
  if (merchant) {
    parts.push(`在${cityLabel(merchant.city)}市场出售，奖励按该市场结算。`);
    parts.push(`市场奖励：${brassMerchantRewardText(merchant.reward)}`);
  } else {
    parts.push("请选择售卖市场并按已选资源来源结算啤酒。");
  }
  return parts.join("\n");
}

function brassPlayerName(players, userId) {
  return players.find((player) => String(player.userId) === String(userId))?.username ?? "";
}

function brassEraText(era) {
  if (era === "canal") return TEXT.canalEra;
  if (era === "rail") return TEXT.railEra;
  return era ?? "";
}

function brassActionText(action) {
  const labels = {
    build: TEXT.brassBuild,
    sell: TEXT.brassSell,
    network: TEXT.brassNetwork,
    develop: TEXT.brassDevelop,
    loan: TEXT.brassLoan,
    scout: TEXT.brassScout,
    skip: TEXT.brassSkip,
    restart_turn: TEXT.brassRestartTurn,
    end_turn: TEXT.brassEndTurn,
    end_sell: "结束售卖行动",
    maintain_era: TEXT.brassMaintainEra,
    resolve_income_debt: "支付收入欠款"
  };
  return labels[action] ?? action;
}

function brassCardKind(card) {
  if (card?.wild || card?.type === "wild_location" || card?.type === "wild_industry") return "wild";
  return card?.type === "industry" ? "industry" : "location";
}

function brassCardKindLabel(card) {
  return {
    wild: "万能牌",
    location: "地点牌",
    industry: "产业牌"
  }[brassCardKind(card)] ?? "卡牌";
}

function brassCardIcon(card) {
  return {
    wild: "✦",
    location: "⌂",
    industry: "⚙"
  }[brassCardKind(card)] ?? "◆";
}

function brassCardTypeText(card) {
  if (card?.wild) return "万能牌";
  if (card?.type === "location") return "地点牌";
  if (card?.type === "industry") {
    const industries = Array.isArray(card.industryTypes) && card.industryTypes.length > 0 ? card.industryTypes : [card.key];
    return `产业牌：${industries.map((industry) => BRASS_INDUSTRIES[industry] ?? industry).join("/")}`;
  }
  return card?.type ?? "";
}

function brassCardName(card) {
  if (!card) return "";
  if (card.displayName) return card.displayName;
  if (card.type === "wild_location" || card.key === "wild_location") return "万能地点牌";
  if (card.type === "wild_industry" || card.key === "wild_industry") return "万能产业牌";
  if (card.wild) return "万能牌";
  if (card.type === "location") return `地点牌：${card.city ?? card.key ?? ""}`;
  if (card.type === "industry") {
    const industries = Array.isArray(card.industryTypes) && card.industryTypes.length > 0 ? card.industryTypes : [card.key].filter(Boolean);
    return `产业牌：${industries.map((industry) => BRASS_INDUSTRIES[industry] ?? industry).join("/")}`;
  }
  return card.name ?? "";
}

function brassCardShortName(card, cities = [], cityNames = {}) {
  if (!card) return "";
  if (card.type === "wild_location" || card.key === "wild_location") return "万能地点牌";
  if (card.type === "wild_industry" || card.key === "wild_industry") return "万能产业牌";
  if (card.wild) return "万能牌";
  if (card.type === "location") return brassCityName(cities, card.city ?? card.key ?? "", cityNames);
  if (card.type === "industry") {
    const industries = Array.isArray(card.industryTypes) && card.industryTypes.length > 0 ? card.industryTypes : [card.key].filter(Boolean);
    return industries.map((industry) => BRASS_INDUSTRIES[industry] ?? industry).join("/");
  }
  return card.displayName ?? card.name ?? "";
}

function brassCardTitleStyle(card, cities = []) {
  if (!card || card.type !== "location") return undefined;
  const city = cities.find((item) => brassMapKey(item.name) === brassMapKey(card.city ?? card.key));
  const color = brassMapColorToCss(city?.color);
  return color ? { color } : undefined;
}

function brassMapColorToCss(color) {
  const key = String(color ?? "").toLowerCase();
  return {
    blue: "#2868b7",
    cyan: "#188d93",
    green: "#2f7d45",
    red: "#9b2d2d",
    "light red": "#b85a58",
    purple: "#7b4bb3",
    lilac: "#7b4bb3",
    orange: "#b56b1d",
    yellow: "#9a7200",
    "light yellow": "#9a7200",
    white: "#1f1a14"
  }[key] ?? "";
}

function brassDevelopmentText(development = {}) {
  const entries = Object.entries(BRASS_INDUSTRIES)
    .map(([key, label]) => `${label}${development[key] ?? 0}`);
  return entries.join(" / ");
}

function brassTurnOrderText(players, turnOrder) {
  if (!turnOrder || turnOrder.length === 0) return "";
  return turnOrder
    .map((userId) => brassPlayerName(players, userId))
    .filter(Boolean)
    .join(" → ");
}

function orderBrassPlayers(players = [], initialTurnOrder = [], currentUserId = "") {
  const playersById = new Map(players.map((player) => [String(player.userId), player]));
  const ordered = [];
  const included = new Set();
  for (const userId of initialTurnOrder ?? []) {
    const key = String(userId);
    const player = playersById.get(key);
    if (player && !included.has(key)) {
      ordered.push(player);
      included.add(key);
    }
  }

  for (const player of players) {
    const key = String(player.userId);
    if (!included.has(key)) {
      ordered.push(player);
      included.add(key);
    }
  }
  const viewerId = String(currentUserId ?? "");
  const viewer = playersById.get(viewerId);
  return viewer
    ? [viewer, ...ordered.filter((player) => String(player.userId) !== viewerId)]
    : ordered;
}

function brassIndustryName(industryType, fallback = "") {
  return BRASS_INDUSTRIES[industryType] ?? fallback ?? industryType;
}

function brassIndustryNames(cityNames = {}) {
  return {
    cotton_mill: BRASS_INDUSTRIES.cotton_mill ?? cityNames.Cotton,
    manufacturer: BRASS_INDUSTRIES.manufacturer ?? cityNames.Manufacture,
    brewery: BRASS_INDUSTRIES.brewery ?? cityNames.Brewery,
    pottery: BRASS_INDUSTRIES.pottery ?? cityNames.Pottery,
    iron_works: BRASS_INDUSTRIES.iron_works ?? cityNames.Iron,
    coal_mine: BRASS_INDUSTRIES.coal_mine ?? cityNames.Coal
  };
}

function brassCityName(cities = [], city, cityNames = {}) {
  const raw = String(city ?? "");
  const upper = raw.toUpperCase();
  return BRASS_CITY_CN[upper]
    ?? BRASS_CITY_CN[raw]
    ?? cityNames[upper]
    ?? cityNames[raw]
    ?? cities.find((item) => item.name === raw || String(item.name).toUpperCase() === upper)?.cnName
    ?? raw;
}

function brassMerchantRewardText(reward = "") {
  const text = String(reward);
  if (text.includes("Income")) return `收入等级 +${text.match(/\d+/)?.[0] ?? 2}`;
  if (text.includes("VP")) return `${text.match(/\d+/)?.[0] ?? 0}VP`;
  if (text.includes("Develop")) return "免费研发1个板块";
  if (text.includes("5")) return "获得5金钱";
  return text || "无";
}

function brassBoardTileTooltip(tile = {}) {
  const period = Number(tile.period ?? 0);
  const eraLimit = period === 1 ? "仅运河时代" : period === 2 ? "仅铁路时代" : "不限时代";
  const details = [
    `${brassIndustryName(tile.industryType, tile.industryName)} ${tile.level ?? ""}级`,
    `建造价格：£${tile.cost ?? 0}`,
    `煤需求：${tile.coalCost ?? 0}`,
    `铁需求：${tile.ironCost ?? 0}`,
    `建造时代限制：${eraLimit}`
  ];
  if (tile.flipType === "deplete") {
    const amounts = Array.isArray(tile.resourceAmounts) ? tile.resourceAmounts : [0, 0];
    const canalCapacity = period === 2 ? 0 : Number(amounts[0] ?? 0);
    const railCapacity = period === 1 ? 0 : Number(amounts[1] ?? amounts[0] ?? 0);
    details.push(`产能：${canalCapacity}/${railCapacity}`);
  }
  details.push(
    `可被研发：${tile.canDevelop === false ? "否" : "是"}`,
    `售卖啤酒需求：${tile.saleBeerCost ?? 0}`,
    `翻面后VP：${tile.victoryPoints ?? 0}`,
    `翻面后路分：${tile.roadPoints ?? 0}`,
    `翻面后收入等级增长：${tile.incomeReward ?? 0}`
  );
  return details.join("\n");
}

function buildBrassMapCities(metadata = [], routes = [], fallbackCities = [], cityNames = {}) {
  const fixedLayout = {
    WARRINGTON: [14, 14],
    "STOKE-ON-TRENT": [30, 12],
    STONE: [37, 25],
    STAFFORD: [45, 38],
    CANNOCK: [56, 51],
    WOLVERHAMPTON: [70, 43],
    BIRMINGHAM: [76, 61],
    COVENTRY: [101, 66],
    NUNEATON: [94, 48],
    TAMWORTH: [78, 39],
    WALSALL: [66, 32],
    "BURTON-ON-TRENT": [65, 19],
    DERBY: [78, 10],
    NOTTINGHAM: [104, 13],
    LEEK: [42, 7],
    UTTOXETER: [54, 16],
    BELPER: [88, 6],
    DUDLEY: [58, 63],
    COALBROOKDALE: [38, 61],
    SHREWSBURY: [18, 61],
    KIDDERMINSTER: [48, 76],
    WORCESTER: [67, 82],
    GLOUCESTER: [80, 91],
    REDDITCH: [88, 77],
    OXFORD: [106, 86],
    PERSONAL_BREWERY: [57, 88],
    RURAL_BREWERY: [48, 50],
    Personal_Brewery: [57, 88],
    Rural_Brewery: [48, 50]
  };
  const normalizedCityName = (name) => {
    const raw = String(name ?? "");
    const upper = raw.toUpperCase();
    return BRASS_CITY_CN[upper] ?? BRASS_CITY_CN[raw] ?? cityNames[upper] ?? cityNames[raw] ?? raw;
  };
  const metaByName = new Map((metadata ?? []).map((city) => [
    city.name,
    { ...city, cnName: normalizedCityName(city.name) ?? city.cnName ?? city.name }
  ]));
  const names = [...new Set([
    ...(metadata ?? []).map((city) => city.name),
    ...fallbackCities,
    ...routes.flatMap((route) => [route.from, route.to]).filter(Boolean)
  ])];
  const colorOrder = ["blue", "cyan", "green", "red", "purple", "orange", "yellow", "white"];
  const colorCenters = {
    blue: [18, 18],
    cyan: [45, 18],
    green: [72, 20],
    red: [22, 48],
    purple: [50, 50],
    orange: [78, 50],
    yellow: [48, 75],
    white: [12, 78]
  };
  const grouped = new Map();
  for (const name of names) {
    const meta = metaByName.get(name) ?? { name, cnName: normalizedCityName(name), color: "white", type: "Industry" };
    const color = meta.color || "white";
    if (!grouped.has(color)) grouped.set(color, []);
    grouped.get(color).push(meta);
  }
  const positions = [];
  for (const color of colorOrder) {
    const cities = grouped.get(color) ?? [];
    const [cx, cy] = colorCenters[color] ?? [50, 50];
    cities.forEach((city, index) => {
      const cols = Math.max(1, Math.ceil(Math.sqrt(cities.length)));
      const row = Math.floor(index / cols);
      const col = index % cols;
      positions.push({
        name: city.name,
        cnName: city.cnName ?? city.name,
        color,
        type: city.type ?? "",
        x: (fixedLayout[city.name] ?? fixedLayout[String(city.name).toUpperCase()])?.[0] ?? cx + (col - (cols - 1) / 2) * 13,
        y: (fixedLayout[city.name] ?? fixedLayout[String(city.name).toUpperCase()])?.[1] ?? cy + (row - Math.floor(cities.length / cols) / 2) * 10
      });
    });
  }
  const byName = new Map(positions.map((city) => [city.name, city]));
  for (const city of positions) {
    if (city.type !== "Market" || city.color !== "white") continue;
    if (fixedLayout[city.name] ?? fixedLayout[String(city.name).toUpperCase()]) continue;
    const neighbors = routes
      .filter((route) => route.from === city.name || route.to === city.name)
      .map((route) => byName.get(route.from === city.name ? route.to : route.from))
      .filter(Boolean);
    if (neighbors.length > 0) {
      city.x = neighbors.reduce((sum, item) => sum + item.x, 0) / neighbors.length + 8;
      city.y = neighbors.reduce((sum, item) => sum + item.y, 0) / neighbors.length + 4;
    }
  }
  return positions;
}

function routeColorStyle(links = [], key) {
  const link = links.find((item) => brassRouteKey(item) === key);
  const color = BRASS_PLAYER_COLORS[link?.color]?.css ?? link?.color;
  return color ? { stroke: color } : undefined;
}

function groupBrassBoardTiles(tiles = []) {
  const groups = new Map();
  for (const tile of tiles) {
    const key = [
      tile.industryType,
      tile.level,
      tile.cost,
      tile.coalCost,
      tile.ironCost,
      tile.period,
      tile.flipType,
      Array.isArray(tile.resourceAmounts) ? tile.resourceAmounts.join("/") : "",
      tile.canDevelop,
      tile.saleBeerCost,
      tile.victoryPoints,
      tile.roadPoints,
      tile.incomeReward
    ].join("|");
    const current = groups.get(key);
    if (current) {
      current.count += 1;
    } else {
      groups.set(key, { ...tile, groupKey: key, count: 1 });
    }
  }
  return [...groups.values()].sort((left, right) => Number(left.level ?? 0) - Number(right.level ?? 0));
}

function objectCounts(values = []) {
  return values.reduce((counts, value) => {
    const key = String(value);
    counts[key] = (counts[key] ?? 0) + 1;
    return counts;
  }, {});
}

function brassRouteKey(route) {
  if (!route) return "";
  const ends = [brassMapKey(route.from), brassMapKey(route.to)].sort();
  return `${ends[0]}|${ends[1]}`;
}

function brassSlotKey(city, slotIndex) {
  return `${brassMapKey(city)}:${Number(slotIndex ?? 0)}`;
}

function brassMapKey(value) {
  const raw = String(value ?? "");
  if (raw.toUpperCase() === "BREWERY") return "PERSONAL_BREWERY";
  return raw.toUpperCase();
}

function brassLayoutRouteKey(route) {
  return brassRouteKey({ from: route.src, to: route.dst });
}

function findRouteForLayout(routes = [], layoutRoute = {}) {
  const src = brassMapKey(layoutRoute.src);
  const dst = brassMapKey(layoutRoute.dst);
  return (routes ?? []).find((route) => {
    const from = brassMapKey(route.from);
    const to = brassMapKey(route.to);
    return (from === src && to === dst) || (from === dst && to === src);
  }) ?? null;
}

function routeTooltip(route, layoutRoute, builtLink, cityByName = new Map(), cityNames = {}) {
  const from = route?.from ?? layoutRoute.src;
  const to = route?.to ?? layoutRoute.dst;
  const fromName = brassCityName([...cityByName.values()], from, cityNames);
  const toName = brassCityName([...cityByName.values()], to, cityNames);
  const lines = [
    `路线：${fromName} - ${toName}`,
    `建造状态：${builtLink ? `玩家${builtLink.ownerName ?? "-"}建造` : "未建造"}`
  ];
  if (layoutRoute?.hasBrewery === true || route?.hasBrewery === true) {
    lines.push("附属酿酒厂：私人酿酒厂");
  }
  return lines.join("\n");
}

function industrySlotTooltip(slot, city, builtTile, cityNames = {}) {
  const cityName = brassCityName([city], slot.city, cityNames);
  if (builtTile) {
    const lines = [
      `玩家：${builtTile.ownerName ?? "-"}`,
      `城市：${cityName}`,
      `产业：${brassIndustryName(builtTile.industryType, builtTile.industryName)}`,
      `等级：${builtTile.level ?? "-"}`,
      `状态：${builtTile.flipped ? "已翻面" : "未翻面"}`
    ];
    if (builtTile.flipped) {
      lines.push(`产业VP：${builtTile.victoryPoints ?? builtTile.vp ?? 0}`);
      lines.push(`路分：${builtTile.roadPoints ?? builtTile.roadPoint ?? 0}`);
    } else if (["coal_mine", "iron_works", "brewery"].includes(builtTile.industryType)) {
      const resourceName = builtTile.industryType === "coal_mine" ? "煤" : builtTile.industryType === "iron_works" ? "铁" : "啤酒";
      const resourceValue = builtTile.remainingResource ?? (builtTile.industryType === "coal_mine" ? builtTile.coal : builtTile.industryType === "iron_works" ? builtTile.iron : builtTile.beer);
      lines.push(`剩余${resourceName}：${resourceValue ?? 0}`);
    } else {
      lines.push(`出售需要啤酒：${builtTile.saleWine ?? builtTile.saleBeerCost ?? 0}`);
    }
    return lines.join("\n");
  }
  const allowed = (slot.allowedIndustryTypesBackend ?? [])
    .map((industryType) => BRASS_INDUSTRIES[industryType] ?? industryType)
    .join("/");
  return [
    `城市：${cityName}`,
    `格位：${Number(slot.slotIndex ?? 0) + 1}号格`,
    `允许产业：${allowed || (slot.allowedIndustryTypes ?? []).join("/") || "-"}`
  ].join("\n");
}

function buildCitySlotInfo(slot, builtTile, cities = [], allIndustries = [], cityNames = {}) {
  const city = cities.find((item) => brassMapKey(item.name) === brassMapKey(slot.city)) ?? { name: slot.city, cnName: slot.city };
  const cityName = brassCityName(cities, slot.city, cityNames);
  const lines = [
    `城市名称：${cityName}`,
    `城市当前路分：${cityCurrentRoadPoint(slot.city, allIndustries)}`,
    `地图格：${Number(slot.slotIndex ?? 0) + 1}号格`
  ];
  if (builtTile) {
    lines.push(`建造情况：玩家${builtTile.ownerName ?? "-"} 的 ${brassIndustryName(builtTile.industryType, builtTile.industryName)}`);
    lines.push(`状态：${builtTile.flipped ? "已翻面" : "未翻面"}`);
    if (builtTile.flipped) {
      lines.push(`产业价值：${builtTile.victoryPoints ?? builtTile.vp ?? 0} VP`);
      lines.push(`提供路分：${builtTile.roadPoints ?? builtTile.roadPoint ?? 0}`);
    } else if (["coal_mine", "iron_works", "brewery"].includes(builtTile.industryType)) {
      const resourceName = builtTile.industryType === "coal_mine" ? "煤" : builtTile.industryType === "iron_works" ? "铁" : "酒";
      const resourceValue = builtTile.remainingResource ?? (builtTile.industryType === "coal_mine" ? builtTile.coal : builtTile.industryType === "iron_works" ? builtTile.iron : builtTile.beer);
      lines.push(`剩余资源：${resourceValue ?? 0} ${resourceName}`);
    } else {
      lines.push(`翻面后预计价值：${builtTile.victoryPoints ?? builtTile.vp ?? 0} VP`);
      lines.push(`翻面后预计路分：${builtTile.roadPoints ?? builtTile.roadPoint ?? 0}`);
    }
  } else {
    const allowed = (slot.allowedIndustryTypesBackend ?? [])
      .map((industryType) => BRASS_INDUSTRIES[industryType] ?? industryType)
      .join(" / ");
    lines.push(`建造情况：可建造${allowed || "-"}`);
  }
  return {
    title: `${cityName}#${Number(slot.slotIndex ?? 0) + 1}`,
    lines
  };
}

function buildRouteInfo(route, links = [], cities = [], cityNames = {}) {
  const builtLink = links.find((link) => brassRouteKey(link) === brassRouteKey(route));
  const from = brassCityName(cities, route.from, cityNames);
  const to = brassCityName(cities, route.to, cityNames);
  const lines = [
    `路线：${from} - ${to}`,
    `建造状态：${builtLink ? `玩家${builtLink.ownerName ?? "-"}建造` : "未建造"}`
  ];
  if (route?.hasBrewery === true) {
    lines.push("附属酿酒厂：私人酿酒厂");
  }
  return { title: "道路信息", lines };
}

function marketSlotsForLayout(layout, merchants = [], playerCount = 0) {
  const configured = layout.marketSlots ?? [];
  const inferred = configured.length ? configured : inferMarketSlotsFromRoutes(layout);
  const merchantsBySlot = new Map((merchants ?? []).map((merchant) => [`${brassMapKey(merchant.city)}:${Number(merchant.slotIndex ?? 0)}`, merchant]));
  const tradableSlotIndexByKey = new Map();
  const tradableCounters = new Map();
  for (const slot of inferred) {
    if (Number(slot.availablePlayers ?? 0) === 0) continue;
    const cityKey = brassMapKey(slot.city);
    const nextIndex = tradableCounters.get(cityKey) ?? 0;
    tradableSlotIndexByKey.set(`${cityKey}:${Number(slot.index ?? 0)}`, nextIndex);
    tradableCounters.set(cityKey, nextIndex + 1);
  }
  return inferred.map((slot) => {
    const cityKey = brassMapKey(slot.city);
    const isDisplayOnly = Number(slot.availablePlayers ?? 0) === 0;
    const isOpenForPlayerCount = isDisplayOnly || Number(slot.availablePlayers ?? 0) <= Number(playerCount ?? 0);
    const merchantSlotIndex = tradableSlotIndexByKey.get(`${cityKey}:${Number(slot.index ?? 0)}`);
    const merchant = isDisplayOnly ? null : merchantsBySlot.get(`${cityKey}:${Number(merchantSlotIndex ?? -1)}`);
    const accepted = (merchant?.acceptedIndustryTypes ?? [])
      .map((industryType) => BRASS_INDUSTRIES[industryType] ?? industryType)
      .join("、");
    const status = isDisplayOnly
      ? "display"
      : !isOpenForPlayerCount || !merchant || merchant.marketOpen === false
      ? "closed"
      : merchant.blank
        ? "blank"
        : "sellable";
    const message = status === "display"
      ? "市场位置展示"
      : status === "closed"
      ? "未开放"
      : status === "blank"
        ? "无可售卖产业"
        : `可以售卖${accepted || "任意产业"}`;
    return {
      ...slot,
      key: `market-${slot.city}-${slot.index}`,
      kind: "market",
      cityName: BRASS_CITY_CN[brassMapKey(slot.city)] ?? slot.cnName ?? slot.city,
      displayOnly: isDisplayOnly,
      merchantId: merchant?.id ?? "",
      merchantSlotIndex,
      status,
      beerStatus: status === "sellable" ? (Number(merchant?.beer ?? 0) > 0 ? "未消耗" : "已消耗") : "未提供",
      playerCount,
      message
    };
  });
}

function inferMarketSlotsFromRoutes(layout) {
  const marketCities = {
    WARRINGTON: { cnName: "沃灵顿", count: 2, availablePlayers: 3 },
    SHREWSBURY: { cnName: "舒兹伯利", count: 1, availablePlayers: 2 },
    NOTTINGHAM: { cnName: "诺丁汉", count: 2, availablePlayers: 4 },
    GLOUCESTER: { cnName: "格罗斯特", count: 2, availablePlayers: 2 },
    OXFORD: { cnName: "牛津", count: 2, availablePlayers: 2 }
  };
  const result = [];
  const byCity = new Map();
  for (const route of layout.routes ?? []) {
    for (const city of [route.src, route.dst]) {
      if (!marketCities[city]) continue;
      const point = route.path?.[route.src === city ? 0 : route.path.length - 1];
      if (point && !byCity.has(city)) byCity.set(city, point);
    }
  }
  for (const [city, meta] of Object.entries(marketCities)) {
    const base = byCity.get(city);
    if (!base) continue;
    for (let index = 0; index < meta.count; index++) {
      result.push({
        city,
        cnName: meta.cnName,
        index,
        availablePlayers: meta.availablePlayers,
        rect: {
          x: Math.max(0.01, Math.min(0.95, base.x + (index - (meta.count - 1) / 2) * 0.028 - 0.012)),
          y: Math.max(0.01, Math.min(0.95, base.y - 0.012)),
          w: 0.024,
          h: 0.024
        }
      });
    }
  }
  return result;
}

function marketSlotTooltip(slot) {
  return [
    `城市名称：${slot.cityName}`,
    `市场信息：${slot.message}`,
    `贸易商啤酒：${slot.beerStatus}`
  ].join("\n");
}

function cityCurrentRoadPoint(city, industries = []) {
  return (industries ?? [])
    .filter((tile) => brassMapKey(tile.city) === brassMapKey(city) && tile.flipped)
    .reduce((sum, tile) => sum + Number(tile.roadPoints ?? tile.roadPoint ?? 0), 0);
}

function shortPlayerName(name = "") {
  const text = String(name ?? "").trim();
  if (text.length <= 5) return text || "-";
  return text.slice(0, 5);
}

function brassIndustryShortName(industryType, fallback = "") {
  const names = {
    cotton_mill: "棉",
    manufacturer: "加",
    brewery: "酒",
    pottery: "陶",
    iron_works: "铁",
    coal_mine: "煤"
  };
  return names[industryType] ?? brassIndustryName(industryType, fallback).slice(0, 1);
}

export { BRASS_CITIES, BRASS_INDUSTRIES, BRASS_PLAYER_COLORS, BRASS_CITY_CN, buildBrassResourceRequirements, resourceSourcesForSelect, buildConfirmText, sellConfirmText, brassPlayerName, brassEraText, brassActionText, brassCardKind, brassCardKindLabel, brassCardIcon, brassCardTypeText, brassCardName, brassCardShortName, brassCardTitleStyle, brassMapColorToCss, brassDevelopmentText, brassTurnOrderText, orderBrassPlayers, brassIndustryName, brassIndustryNames, brassCityName, brassMerchantRewardText, brassBoardTileTooltip, buildBrassMapCities, routeColorStyle, groupBrassBoardTiles, objectCounts, brassRouteKey, brassSlotKey, brassMapKey, brassLayoutRouteKey, findRouteForLayout, routeTooltip, industrySlotTooltip, buildCitySlotInfo, buildRouteInfo, marketSlotsForLayout, inferMarketSlotsFromRoutes, marketSlotTooltip, cityCurrentRoadPoint, shortPlayerName, brassIndustryShortName };
