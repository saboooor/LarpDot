export const PRESET_TYPE = 'larpdot-custom-island-preset' as const;
export const BUNDLE_TYPE = 'larpdot-custom-island-bundle' as const;
export const PRESET_VERSION = 1 as const;
export const SHARE_CODE_PREFIX = 'LARPDOT1:' as const;
export const MAX_IMPORT_BYTES = 256 * 1024;

export const VISIBILITIES = [
  'ALWAYS',
  'CONDITIONAL',
  'IDLE',
  'SWIPE_DOWN',
] as const;
export type CustomIslandVisibility = (typeof VISIBILITIES)[number];

export const CONDITIONS = [
  'MUSIC',
  'FLASHLIGHT',
  'LIVE_ACTIVITY',
  'NOTIFICATION',
] as const;
export type CustomIslandCondition = (typeof CONDITIONS)[number];

export const TAP_ACTIONS = [
  'EXPAND',
  'OPEN_APP',
  'OPEN_NOTIFICATION',
  'RUN_NOTIFICATION_ACTION',
  'NONE',
] as const;
export type CustomIslandTapAction = (typeof TAP_ACTIONS)[number];

export const COLOR_MODES = ['MATERIAL_YOU', 'DOMINANT', 'CUSTOM'] as const;
export type CustomColorMode = (typeof COLOR_MODES)[number];

export const TEXT_ALIGNMENTS = ['START', 'CENTER', 'END'] as const;
export type CustomTextAlignment = (typeof TEXT_ALIGNMENTS)[number];

export const SLOT_IMAGES = [
  'APP_ICON',
  'NOTIFICATION_ICON',
  'ALBUM_ART',
  'NOTIFICATION_IMAGE',
] as const;
export type CustomIslandImage = (typeof SLOT_IMAGES)[number];

export const BACKGROUND_STYLES = [
  'NONE',
  'BASIC_FADED',
  'BLENDED',
  'FULL_BACKGROUND',
  'BLURRED_FULL_BACKGROUND',
] as const;
export type ExpandedBackgroundStyle = (typeof BACKGROUND_STYLES)[number];

export const COMPONENT_KINDS = [
  'TEXT',
  'BUTTONS',
  'ALBUM_ART',
  'NOTIFICATION_IMAGE',
  'APP_ICON',
  'NOTIFICATION_ICON',
  'NOTIFICATION_CONTENT',
] as const;
export type PortableComponentKind = (typeof COMPONENT_KINDS)[number];

export const SLOT_POSITIONS = [
  'LEFT',
  'RIGHT',
  'MAIN',
  'EXPANDED_LEFT',
  'EXPANDED_RIGHT',
] as const;
export type CustomIslandPosition = (typeof SLOT_POSITIONS)[number];

export const ISLAND_ICONS = [
  'APPS',
  'MUSIC',
  'FLASHLIGHT',
  'BATTERY',
  'HEADPHONES',
  'TIMER',
  'CALL',
  'STAR',
  'NAVIGATION',
  'NOTIFICATIONS',
  'HOME',
  'PHOTO',
  'ANDROID',
  'AUDIO_TRACK',
  'BATTERY_ALERT',
  'BATTERY_SAVER',
  'BLUETOOTH',
  'BRIGHTNESS',
  'BUBBLES',
  'BUG',
  'CAR',
  'HOURGLASS',
  'DOWNLOAD',
  'EARBUDS',
  'GAME',
  'HEARING',
  'KEYBOARD',
  'LAPTOP',
  'LAYERS',
  'LOCK',
  'MIC',
  'MOUSE',
  'PALETTE',
  'PAUSE',
  'PHONE',
  'PIN',
  'PLAY',
  'POWER',
  'SCREENSHOT',
  'SEARCH',
  'SECURITY',
  'SETTINGS',
  'SHARE',
  'SHUFFLE',
  'SPEAKER',
  'SPEED',
  'SUN',
  'TV',
  'UPLOAD',
  'USB',
  'VIBRATION',
  'VOLUME',
  'VOLUME_OFF',
  'WATCH',
  'WAVEFORM',
] as const;
export type CustomIslandIcon = (typeof ISLAND_ICONS)[number];

export interface CustomIslandColor {
  mode: CustomColorMode;
  rgb: number;
}

export interface CustomTextStyle {
  size: number;
  rgb: number;
  colorMode: CustomColorMode;
  bold: boolean;
  italic: boolean;
  underline: boolean;
  alignment: CustomTextAlignment;
}

export interface CustomButtonStyle {
  playBackground: boolean;
  playAccent: boolean;
  skipBackground: boolean;
  radius: number;
  connected: boolean;
}

export const NOTIFICATION_FIELDS = ['TITLE', 'TEXT', 'CHANNEL'] as const;
export type CustomNotificationField = (typeof NOTIFICATION_FIELDS)[number];

export const NOTIFICATION_OPERATORS = ['CONTAINS', 'NOT_CONTAINS'] as const;
export type CustomNotificationOperator =
  (typeof NOTIFICATION_OPERATORS)[number];

export const CONDITION_JOINS = ['AND', 'OR'] as const;
export type CustomConditionJoin = (typeof CONDITION_JOINS)[number];

export interface CustomNotificationCondition {
  field: CustomNotificationField;
  operator: CustomNotificationOperator;
  pattern: string;
  join: CustomConditionJoin;
}

export interface CustomNotificationRule {
  package: string;
  channel: string;
  title: string;
  text: string;
  matchChannel: boolean;
  minimizeNotification?: boolean;
  conditions?: CustomNotificationCondition[];
}

export interface CustomIslandSlot {
  icon?: CustomIslandIcon;
  image?: CustomIslandImage;
  iconColor?: CustomIslandColor;
  text?: string;
  textStyle?: CustomTextStyle;
  width: number;
  height: number;
  paddingX: number;
  paddingY: number;
  spanFullIsland?: boolean;
}

export interface CustomIslandComponent {
  kind: PortableComponentKind;
  height: number;
  text?: string;
  textStyle?: CustomTextStyle;
  buttonStyle?: CustomButtonStyle;
}

export interface PortableCustomIsland {
  name: string;
  visibility: CustomIslandVisibility;
  condition: CustomIslandCondition;
  tapAction: CustomIslandTapAction;
  notificationActionTitle?: string;
  tapAppPackage?: string;
  activityKind?: string;
  backgroundStyle?: ExpandedBackgroundStyle;
  backgroundImage?: CustomIslandImage;
  notificationRule: CustomNotificationRule;
  components: CustomIslandComponent[];
  LEFT: CustomIslandSlot;
  RIGHT: CustomIslandSlot;
  MAIN: CustomIslandSlot;
  EXPANDED_LEFT: CustomIslandSlot;
  EXPANDED_RIGHT: CustomIslandSlot;
}

export interface LarpDotPresetEnvelope {
  type: typeof PRESET_TYPE;
  version: typeof PRESET_VERSION;
  name: string;
  description?: string;
  omittedWidgets?: number;
  island: PortableCustomIsland;
}

export interface LarpDotBundlePresetItem {
  name: string;
  description?: string;
  island: PortableCustomIsland;
}

export interface LarpDotBundleEnvelope {
  type: typeof BUNDLE_TYPE;
  version: typeof PRESET_VERSION;
  name: string;
  description?: string;
  appPackage?: string;
  appName?: string;
  omittedWidgets?: number;
  presets: LarpDotBundlePresetItem[];
}

export type LarpDotShareEnvelope = LarpDotPresetEnvelope | LarpDotBundleEnvelope;

export interface DecodedLarpDotShare {
  isBundle: boolean;
  name: string;
  description: string;
  appPackage: string | null;
  appName: string | null;
  omittedWidgets: number;
  islands: Array<{
    name: string;
    description: string;
    island: PortableCustomIsland;
  }>;
  envelope: LarpDotShareEnvelope;
}

const POSITION_BOUNDS: Record<
  CustomIslandPosition,
  { minW: number; maxW: number; defW: number; defH: number }
> = {
  LEFT: { minW: 24, maxW: 160, defW: 64, defH: 24 },
  RIGHT: { minW: 24, maxW: 160, defW: 64, defH: 24 },
  MAIN: { minW: 80, maxW: 440, defW: 300, defH: 150 },
  EXPANDED_LEFT: { minW: 36, maxW: 140, defW: 44, defH: 44 },
  EXPANDED_RIGHT: { minW: 36, maxW: 140, defW: 44, defH: 44 },
};

const KNOWN_APPS: Record<string, string> = {
  'com.spotify.music': 'Spotify',
  'com.google.android.apps.youtube.music': 'YouTube Music',
  'com.google.android.youtube': 'YouTube',
  'com.apple.android.music': 'Apple Music',
  'com.soundcloud.android': 'SoundCloud',
  'com.google.android.apps.maps': 'Google Maps',
  'com.waze': 'Waze',
  'com.discord': 'Discord',
  'com.whatsapp': 'WhatsApp',
  'org.telegram.messenger': 'Telegram',
  'com.instagram.android': 'Instagram',
  'com.snapchat.android': 'Snapchat',
  'com.Slack': 'Slack',
  'com.google.android.apps.messaging': 'Google Messages',
  'com.google.android.deskclock': 'Clock',
  'com.android.deskclock': 'Clock',
  'com.ubercab': 'Uber',
  'com.ubercab.eats': 'Uber Eats',
  'com.dd.doordash': 'DoorDash',
  'com.strava': 'Strava',
  'com.google.android.dialer': 'Phone',
  'com.android.chrome': 'Chrome',
  'com.twitch.android.app': 'Twitch',
  'com.netflix.mediaclient': 'Netflix',
};

export function formatAppDisplayName(
  appPackage?: string | null,
  explicitAppName?: string | null
): string | null {
  if (explicitAppName?.trim()) return explicitAppName.trim();
  const pkg = appPackage?.trim();
  if (!pkg) return null;
  if (KNOWN_APPS[pkg]) return KNOWN_APPS[pkg];
  const segments = pkg
    .split('.')
    .filter(
      (part) =>
        part.length > 0 &&
        !['com', 'org', 'net', 'io', 'app', 'apps', 'android', 'google'].includes(
          part.toLowerCase()
        )
    );
  const candidate = segments.at(-1) ?? pkg;
  return candidate
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (ch) => ch.toUpperCase());
}

export function extractIslandAppPackage(
  island: PortableCustomIsland
): string | null {
  const notifPkg = island.notificationRule.package.trim();
  if (notifPkg) return notifPkg;
  const tapPkg = island.tapAppPackage?.trim();
  if (tapPkg) return tapPkg;
  return null;
}

export function slotHasContent(slot: CustomIslandSlot): boolean {
  return Boolean(slot.icon || slot.image || slot.text?.trim());
}

export function islandHasExpandedContent(island: PortableCustomIsland): boolean {
  return (
    island.components.length > 0 ||
    slotHasContent(island.EXPANDED_LEFT) ||
    slotHasContent(island.EXPANDED_RIGHT)
  );
}

export function formatVisibilityLabel(
  visibility: string,
  condition?: string | null
): string {
  switch (visibility) {
    case 'ALWAYS':
      return 'Always';
    case 'IDLE':
      return 'Idle';
    case 'SWIPE_DOWN':
      return 'Swipe down';
    case 'CONDITIONAL':
      return condition ? `On ${formatConditionLabel(condition)}` : 'Conditional';
    case 'MIXED':
      return 'Mixed triggers';
    default:
      return visibility;
  }
}

export function formatConditionLabel(condition: string): string {
  switch (condition) {
    case 'MUSIC':
      return 'Music';
    case 'FLASHLIGHT':
      return 'Flashlight';
    case 'LIVE_ACTIVITY':
      return 'Live Activity';
    case 'NOTIFICATION':
      return 'Notification';
    default:
      return condition;
  }
}

export function formatTapActionLabel(action: CustomIslandTapAction): string {
  switch (action) {
    case 'EXPAND':
      return 'Expand island';
    case 'OPEN_APP':
      return 'Open app';
    case 'OPEN_NOTIFICATION':
      return 'Open notification';
    case 'RUN_NOTIFICATION_ACTION':
      return 'Run notification action';
    case 'NONE':
      return 'None';
  }
}

export function formatImageLabel(image: CustomIslandImage): string {
  switch (image) {
    case 'APP_ICON':
      return 'App Icon';
    case 'NOTIFICATION_ICON':
      return 'Notification Icon';
    case 'ALBUM_ART':
      return 'Album Art';
    case 'NOTIFICATION_IMAGE':
      return 'Notification Image';
  }
}

export function formatComponentKindLabel(kind: PortableComponentKind): string {
  switch (kind) {
    case 'TEXT':
      return 'Text';
    case 'BUTTONS':
      return 'Playback Controls';
    case 'ALBUM_ART':
      return 'Album Art';
    case 'NOTIFICATION_IMAGE':
      return 'Notification Image';
    case 'APP_ICON':
      return 'App Icon';
    case 'NOTIFICATION_ICON':
      return 'Notification Icon';
    case 'NOTIFICATION_CONTENT':
      return 'Notification Content';
  }
}

export function formatIconLabel(icon: CustomIslandIcon): string {
  return icon
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

export function rgbIntToHex(rgb: number): string {
  const clamped = clampInt(rgb, 0, 0xffffff, 0xffffff);
  return `#${clamped.toString(16).padStart(6, '0')}`;
}

export function resolvePreviewColor(
  mode: CustomColorMode | undefined,
  rgb: number | undefined,
  accentHex = '#7c8cff'
): string {
  if (mode === 'CUSTOM') return rgbIntToHex(rgb ?? 0xffffff);
  if (mode === 'DOMINANT') return '#38bdf8';
  return accentHex;
}

export function getEnvelopeIslands(
  envelope: LarpDotShareEnvelope
): Array<{ name: string; description: string; island: PortableCustomIsland }> {
  if (envelope.type === BUNDLE_TYPE) {
    return envelope.presets.map((item) => ({
      name: item.name,
      description: item.description ?? '',
      island: item.island,
    }));
  }
  return [
    {
      name: envelope.name,
      description: envelope.description ?? '',
      island: envelope.island,
    },
  ];
}

export function summarizeEnvelopeTrigger(envelope: LarpDotShareEnvelope): {
  visibility: string;
  condition: string | null;
} {
  const islands = getEnvelopeIslands(envelope);
  const visSet = new Set(islands.map((item) => item.island.visibility));
  const condSet = new Set(
    islands
      .filter((item) => item.island.visibility === 'CONDITIONAL')
      .map((item) => item.island.condition)
  );
  const visibility =
    visSet.size === 1 ? (islands[0]?.island.visibility ?? 'IDLE') : 'MIXED';
  const condition =
    condSet.size === 1 ? (Array.from(condSet)[0] ?? null) : null;
  return { visibility, condition };
}

export function formatShareJson(envelope: LarpDotShareEnvelope): string {
  return JSON.stringify(envelope, null, 2);
}

export function createPresetSlug(name: string): string {
  const base = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 42);
  const bytes = new Uint8Array(3);
  crypto.getRandomValues(bytes);
  const suffix = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join(
    ''
  );
  return `${base || 'preset'}-${suffix}`;
}

export function extractShareCodeToken(raw: string): string | null {
  const idx = raw.indexOf(SHARE_CODE_PREFIX);
  if (idx < 0) return null;
  const start = idx + SHARE_CODE_PREFIX.length;
  let end = raw.length;
  for (let i = start; i < raw.length; i++) {
    const ch = raw[i];
    if (ch === ' ' || ch === '\n' || ch === '\r' || ch === '\t') {
      end = i;
      break;
    }
  }
  const token = raw.slice(start, end).trim();
  return token.length > 0 ? token : null;
}

export async function encodeShareCode(
  envelope: LarpDotShareEnvelope
): Promise<string> {
  const compact = JSON.stringify(envelope);
  const utf8 = new TextEncoder().encode(compact);
  const compressed = await gzipCompress(utf8);
  return `${SHARE_CODE_PREFIX}${bytesToBase64Url(compressed)}`;
}

export async function decodeShareInput(
  raw: string
): Promise<DecodedLarpDotShare | null> {
  const trimmed = raw.trim();
  if (!trimmed) return null;

  let jsonText = trimmed;
  const token = extractShareCodeToken(trimmed);
  if (token) {
    const decompressed = await decompressShareToken(token);
    if (!decompressed) return null;
    jsonText = decompressed;
  }

  return parseShareJson(jsonText);
}

export function parseShareJson(raw: string): DecodedLarpDotShare | null {
  if (new TextEncoder().encode(raw).byteLength > MAX_IMPORT_BYTES) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!isRecord(parsed)) return null;

  const version = readInt(parsed.version, -1);
  if (version < 1 || version > PRESET_VERSION) return null;

  const type = readString(parsed.type);
  if (type === PRESET_TYPE) {
    if (!isRecord(parsed.island)) return null;
    const island = normalizeIsland(parsed.island);
    if (!island) return null;

    const name = (
      readString(parsed.name).trim() ||
      island.name.trim() ||
      'Imported Preset'
    ).slice(0, 48);
    const description = readString(parsed.description).trim().slice(0, 160);
    const omittedWidgets = Math.max(0, readInt(parsed.omittedWidgets, 0));
    const normalizedIsland: PortableCustomIsland = { ...island, name };
    const appPackage = extractIslandAppPackage(normalizedIsland);

    const envelope: LarpDotPresetEnvelope = {
      type: PRESET_TYPE,
      version: PRESET_VERSION,
      name,
      ...(description ? { description } : {}),
      ...(omittedWidgets > 0 ? { omittedWidgets } : {}),
      island: normalizedIsland,
    };

    return {
      isBundle: false,
      name,
      description,
      appPackage,
      appName: formatAppDisplayName(appPackage),
      omittedWidgets,
      islands: [{ name, description, island: normalizedIsland }],
      envelope,
    };
  }

  if (type === BUNDLE_TYPE) {
    if (!Array.isArray(parsed.presets)) return null;
    const items: LarpDotBundlePresetItem[] = [];
    for (const entry of parsed.presets) {
      if (!isRecord(entry) || !isRecord(entry.island)) continue;
      const island = normalizeIsland(entry.island);
      if (!island) continue;
      const itemName = (
        readString(entry.name).trim() ||
        island.name.trim() ||
        'Custom Island'
      ).slice(0, 48);
      const itemDesc = readString(entry.description).trim().slice(0, 160);
      items.push({
        name: itemName,
        ...(itemDesc ? { description: itemDesc } : {}),
        island: { ...island, name: itemName },
      });
    }
    if (items.length === 0) return null;

    const explicitPkg = readString(parsed.appPackage).trim().slice(0, 200);
    const appPackage =
      explicitPkg ||
      items
        .map((item) => extractIslandAppPackage(item.island))
        .find((pkg): pkg is string => Boolean(pkg)) ||
      null;
    const explicitAppName = readString(parsed.appName).trim().slice(0, 64);
    const appName = formatAppDisplayName(appPackage, explicitAppName);
    const bundleName = (
      readString(parsed.name).trim() ||
      appName ||
      'App Bundle'
    ).slice(0, 48);
    const description = readString(parsed.description).trim().slice(0, 160);
    const omittedWidgets = Math.max(0, readInt(parsed.omittedWidgets, 0));

    const envelope: LarpDotBundleEnvelope = {
      type: BUNDLE_TYPE,
      version: PRESET_VERSION,
      name: bundleName,
      ...(description ? { description } : {}),
      ...(appPackage ? { appPackage } : {}),
      ...(explicitAppName || appName
        ? { appName: explicitAppName || appName || undefined }
        : {}),
      ...(omittedWidgets > 0 ? { omittedWidgets } : {}),
      presets: items,
    };

    return {
      isBundle: true,
      name: bundleName,
      description,
      appPackage,
      appName,
      omittedWidgets,
      islands: items.map((item) => ({
        name: item.name,
        description: item.description ?? '',
        island: item.island,
      })),
      envelope,
    };
  }

  return null;
}

export function applyMetadataToEnvelope(
  decoded: DecodedLarpDotShare,
  meta: { name: string; description: string; appName?: string | null }
): LarpDotShareEnvelope {
  const cleanName = meta.name.trim().slice(0, 48) || decoded.name;
  const cleanDesc = meta.description.trim().slice(0, 160);
  if (decoded.envelope.type === BUNDLE_TYPE) {
    const cleanAppName = meta.appName?.trim().slice(0, 64) || undefined;
    return {
      ...decoded.envelope,
      name: cleanName,
      ...(cleanDesc ? { description: cleanDesc } : { description: undefined }),
      ...(cleanAppName ? { appName: cleanAppName } : {}),
    };
  }
  return {
    ...decoded.envelope,
    name: cleanName,
    ...(cleanDesc ? { description: cleanDesc } : { description: undefined }),
    island: {
      ...decoded.envelope.island,
      name: cleanName,
    },
  };
}

function normalizeIsland(
  raw: Record<string, unknown>
): PortableCustomIsland | null {
  const visibility = readEnum(raw.visibility, VISIBILITIES, 'IDLE');
  const condition = readEnum(raw.condition, CONDITIONS, 'MUSIC');
  const tapAction = readEnum(raw.tapAction, TAP_ACTIONS, 'EXPAND');
  const name = readString(raw.name).trim().slice(0, 48);
  const notificationActionTitle = readString(raw.notificationActionTitle)
    .trim()
    .slice(0, 64);
  const tapAppPackage = readString(raw.tapAppPackage).trim().slice(0, 200);
  const activityKind = readString(raw.activityKind).trim().slice(0, 48);

  const rawRule = isRecord(raw.notificationRule) ? raw.notificationRule : {};
  const ruleConditions: CustomNotificationCondition[] = [];
  if (Array.isArray(rawRule.conditions)) {
    for (const item of rawRule.conditions) {
      if (!isRecord(item)) continue;
      ruleConditions.push({
        field: readEnum(item.field, NOTIFICATION_FIELDS, 'TITLE'),
        operator: readEnum(item.operator, NOTIFICATION_OPERATORS, 'CONTAINS'),
        pattern: readString(item.pattern).slice(0, 1000),
        join: readEnum(item.join, CONDITION_JOINS, 'AND'),
      });
    }
  }
  const notificationRule: CustomNotificationRule = {
    package: readString(rawRule.package).trim().slice(0, 200),
    channel: readString(rawRule.channel).trim().slice(0, 200),
    title: readString(rawRule.title).trim().slice(0, 250),
    text: readString(rawRule.text).trim().slice(0, 1000),
    matchChannel: Boolean(rawRule.matchChannel),
    ...(rawRule.minimizeNotification ? { minimizeNotification: true } : {}),
    ...(ruleConditions.length > 0 ? { conditions: ruleConditions } : {}),
  };

  const components: CustomIslandComponent[] = [];
  if (Array.isArray(raw.components)) {
    for (const item of raw.components) {
      if (!isRecord(item)) continue;
      const kindStr = readString(item.kind);
      if (kindStr === 'WIDGET') continue;
      const kind = COMPONENT_KINDS.find((k) => k === kindStr);
      if (!kind) continue;

      const comp: CustomIslandComponent = {
        kind,
        height: clampInt(item.height, 24, 300, 150),
      };
      const text = readString(item.text).slice(0, 240);
      if (text) comp.text = text;
      if (kind === 'TEXT') {
        comp.textStyle = normalizeTextStyle(item.textStyle, 14, 'START');
      }
      if (kind === 'BUTTONS') {
        const bs = isRecord(item.buttonStyle) ? item.buttonStyle : {};
        comp.buttonStyle = {
          playBackground:
            typeof bs.playBackground === 'boolean' ? bs.playBackground : true,
          playAccent:
            typeof bs.playAccent === 'boolean' ? bs.playAccent : false,
          skipBackground:
            typeof bs.skipBackground === 'boolean' ? bs.skipBackground : false,
          radius: clampNumber(bs.radius, 0, 32, 20),
          connected: typeof bs.connected === 'boolean' ? bs.connected : false,
        };
      }
      components.push(comp);
    }
  }

  const backgroundStyle = BACKGROUND_STYLES.find(
    (style) => style === readString(raw.backgroundStyle)
  );
  const backgroundImage = SLOT_IMAGES.find(
    (img) => img === readString(raw.backgroundImage)
  );

  return {
    name,
    visibility,
    condition,
    tapAction,
    ...(notificationActionTitle ? { notificationActionTitle } : {}),
    ...(tapAppPackage ? { tapAppPackage } : {}),
    ...(activityKind ? { activityKind } : {}),
    ...(backgroundStyle && backgroundStyle !== 'NONE'
      ? { backgroundStyle }
      : {}),
    ...(backgroundImage ? { backgroundImage } : {}),
    notificationRule,
    components,
    LEFT: normalizeSlot('LEFT', raw.LEFT),
    RIGHT: normalizeSlot('RIGHT', raw.RIGHT),
    MAIN: normalizeSlot('MAIN', raw.MAIN),
    EXPANDED_LEFT: normalizeSlot('EXPANDED_LEFT', raw.EXPANDED_LEFT),
    EXPANDED_RIGHT: normalizeSlot('EXPANDED_RIGHT', raw.EXPANDED_RIGHT),
  };
}

function normalizeSlot(
  position: CustomIslandPosition,
  raw: unknown
): CustomIslandSlot {
  const bounds = POSITION_BOUNDS[position];
  const entry = isRecord(raw) ? raw : {};

  const isMain = position === 'MAIN';
  const rawText =
    !isMain && typeof entry.text === 'string'
      ? entry.text.slice(0, 120)
      : undefined;
  const hasText = rawText !== undefined;

  const icon =
    !isMain && !hasText
      ? ISLAND_ICONS.find((candidate) => candidate === readString(entry.icon))
      : undefined;
  const image =
    !isMain && !hasText
      ? SLOT_IMAGES.find((candidate) => candidate === readString(entry.image))
      : undefined;

  const supportsIconColor = Boolean(icon || image === 'NOTIFICATION_ICON');
  let iconColor: CustomIslandColor | undefined;
  if (supportsIconColor) {
    const colorObj = isRecord(entry.iconColor) ? entry.iconColor : {};
    iconColor = {
      mode: readEnum(colorObj.mode, COLOR_MODES, 'MATERIAL_YOU'),
      rgb: clampInt(colorObj.rgb, 0, 0xffffff, 0xffffff),
    };
  }

  const slot: CustomIslandSlot = {
    ...(icon ? { icon } : {}),
    ...(image ? { image } : {}),
    ...(iconColor ? { iconColor } : {}),
    ...(hasText
      ? {
          text: rawText,
          textStyle: normalizeTextStyle(entry.textStyle, 12, 'CENTER'),
        }
      : {}),
    width: clampInt(entry.width, bounds.minW, bounds.maxW, bounds.defW),
    height: clampInt(entry.height, 1, 300, bounds.defH),
    paddingX: clampInt(entry.paddingX, 0, 32, 8),
    paddingY: clampInt(entry.paddingY, 0, 32, 8),
    ...(isMain && entry.spanFullIsland === true
      ? { spanFullIsland: true }
      : {}),
  };

  return slot;
}

function normalizeTextStyle(
  raw: unknown,
  defaultSize: number,
  defaultAlignment: CustomTextAlignment
): CustomTextStyle {
  const obj = isRecord(raw) ? raw : {};
  return {
    size: clampInt(obj.size, 8, 64, defaultSize),
    rgb: clampInt(obj.rgb, 0, 0xffffff, 0xffffff),
    colorMode: readEnum(obj.colorMode, COLOR_MODES, 'CUSTOM'),
    bold: Boolean(obj.bold),
    italic: Boolean(obj.italic),
    underline: Boolean(obj.underline),
    alignment: readEnum(obj.alignment, TEXT_ALIGNMENTS, defaultAlignment),
  };
}

async function gzipCompress(input: Uint8Array): Promise<Uint8Array> {
  const stream = new CompressionStream('gzip');
  const writer = stream.writable.getWriter();
  const copy = new Uint8Array(input.byteLength);
  copy.set(input);
  await writer.write(copy);
  await writer.close();
  return readStreamWithLimit(stream.readable, MAX_IMPORT_BYTES);
}

async function decompressShareToken(token: string): Promise<string | null> {
  try {
    const compressed = base64UrlToBytes(token);
    if (
      !compressed ||
      compressed.byteLength < 2 ||
      compressed.byteLength > MAX_IMPORT_BYTES ||
      compressed[0] !== 0x1f ||
      compressed[1] !== 0x8b
    ) {
      return null;
    }
    const stream = new DecompressionStream('gzip');
    const writer = stream.writable.getWriter();
    const copy = new Uint8Array(compressed.byteLength);
    copy.set(compressed);
    const writePromise = writer
      .write(copy)
      .then(() => writer.close())
      .catch(() => undefined);
    const decompressed = await readStreamWithLimit(
      stream.readable,
      MAX_IMPORT_BYTES
    );
    await writePromise;
    return new TextDecoder().decode(decompressed);
  } catch {
    return null;
  }
}

async function readStreamWithLimit(
  readable: ReadableStream<Uint8Array>,
  maxBytes: number
): Promise<Uint8Array> {
  const reader = readable.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    if (!value) continue;
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel();
      throw new Error('Payload exceeds max size');
    }
    chunks.push(value);
  }
  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return merged;
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function base64UrlToBytes(input: string): Uint8Array | null {
  if (!/^[A-Za-z0-9_-]+$/.test(input)) return null;
  const padded = input
    .replace(/-/g, '+')
    .replace(/_/g, '/')
    .padEnd(Math.ceil(input.length / 4) * 4, '=');
  try {
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function readInt(value: unknown, fallback: number): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.trunc(value);
  }
  return fallback;
}

function clampInt(
  value: unknown,
  min: number,
  max: number,
  fallback: number
): number {
  const num = readInt(value, fallback);
  return Math.max(min, Math.min(max, num));
}

function clampNumber(
  value: unknown,
  min: number,
  max: number,
  fallback: number
): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback;
  return Math.max(min, Math.min(max, value));
}

function readEnum<T extends string>(
  value: unknown,
  allowed: readonly T[],
  fallback: T
): T {
  const str = readString(value);
  return allowed.find((item) => item === str) ?? fallback;
}
