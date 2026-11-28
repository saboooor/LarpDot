import { describe, expect, it } from 'vite-plus/test';
import {
  BUNDLE_TYPE,
  decodeShareInput,
  encodeShareCode,
  formatAppDisplayName,
  PRESET_TYPE,
  PRESET_VERSION,
  SHARE_CODE_PREFIX,
  type LarpDotBundleEnvelope,
  type LarpDotPresetEnvelope,
  type PortableCustomIsland,
} from './presets';

const sampleIsland: PortableCustomIsland = {
  name: 'Spotify Pill',
  visibility: 'CONDITIONAL',
  condition: 'NOTIFICATION',
  tapAction: 'OPEN_APP',
  tapAppPackage: 'com.spotify.music',
  notificationRule: {
    package: 'com.spotify.music',
    channel: '',
    title: '',
    text: '',
    matchChannel: false,
    conditions: [
      { field: 'TITLE', operator: 'CONTAINS', pattern: 'Now Playing', join: 'AND' },
      { field: 'TITLE', operator: 'CONTAINS', pattern: 'Podcast', join: 'OR' },
    ],
  },
  components: [
    {
      kind: 'TEXT',
      height: 44,
      text: 'Now playing on Spotify',
      textStyle: {
        size: 14,
        rgb: 0x1db954,
        colorMode: 'CUSTOM',
        bold: true,
        italic: false,
        underline: false,
        alignment: 'START',
      },
    },
    {
      kind: 'BUTTONS',
      height: 56,
      buttonStyle: {
        playBackground: true,
        playAccent: true,
        skipBackground: false,
        radius: 20,
        connected: true,
      },
    },
  ],
  LEFT: {
    image: 'ALBUM_ART',
    width: 40,
    height: 24,
    paddingX: 8,
    paddingY: 6,
  },
  RIGHT: {
    icon: 'WAVEFORM',
    iconColor: { mode: 'CUSTOM', rgb: 0x1db954 },
    width: 40,
    height: 24,
    paddingX: 8,
    paddingY: 6,
  },
  MAIN: {
    width: 320,
    height: 150,
    paddingX: 12,
    paddingY: 12,
  },
  EXPANDED_LEFT: {
    image: 'APP_ICON',
    width: 44,
    height: 44,
    paddingX: 8,
    paddingY: 8,
  },
  EXPANDED_RIGHT: {
    icon: 'MUSIC',
    iconColor: { mode: 'MATERIAL_YOU', rgb: 0xffffff },
    width: 44,
    height: 44,
    paddingX: 8,
    paddingY: 8,
  },
};

describe('LarpDot preset codec', () => {
  it('round-trips a single preset via LARPDOT1 share code', async () => {
    const envelope: LarpDotPresetEnvelope = {
      type: PRESET_TYPE,
      version: PRESET_VERSION,
      name: 'Spotify Pill',
      description: 'Compact album art with green waveform.',
      island: sampleIsland,
    };

    const code = await encodeShareCode(envelope);
    expect(code.startsWith(SHARE_CODE_PREFIX)).toBe(true);

    const decoded = await decodeShareInput(`Check out my island: ${code}\n`);
    expect(decoded).not.toBeNull();
    expect(decoded?.isBundle).toBe(false);
    expect(decoded?.name).toBe('Spotify Pill');
    expect(decoded?.appPackage).toBe('com.spotify.music');
    expect(decoded?.appName).toBe('Spotify');
    expect(decoded?.islands).toHaveLength(1);
    expect(decoded?.islands[0].island.RIGHT.icon).toBe('WAVEFORM');
    expect(decoded?.islands[0].island.RIGHT.iconColor?.rgb).toBe(0x1db954);
    expect(decoded?.islands[0].island.notificationRule.conditions).toHaveLength(
      2
    );
    expect(decoded?.islands[0].island.notificationRule.conditions?.[1].join).toBe(
      'OR'
    );
  });

  it('round-trips a multi-island app bundle via LARPDOT1 share code', async () => {
    const bundle: LarpDotBundleEnvelope = {
      type: BUNDLE_TYPE,
      version: PRESET_VERSION,
      name: 'Spotify Pack',
      description: 'Compact pill and swipe-down music sheet.',
      appPackage: 'com.spotify.music',
      appName: 'Spotify',
      presets: [
        { name: 'Spotify Pill', island: sampleIsland },
        {
          name: 'Spotify Controls',
          island: {
            ...sampleIsland,
            name: 'Spotify Controls',
            visibility: 'SWIPE_DOWN',
          },
        },
      ],
    };

    const code = await encodeShareCode(bundle);
    const decoded = await decodeShareInput(code);

    expect(decoded).not.toBeNull();
    expect(decoded?.isBundle).toBe(true);
    expect(decoded?.name).toBe('Spotify Pack');
    expect(decoded?.appPackage).toBe('com.spotify.music');
    expect(decoded?.islands).toHaveLength(2);
    expect(decoded?.islands[1].island.visibility).toBe('SWIPE_DOWN');
  });

  it('rejects malformed input and strips WIDGET components', async () => {
    expect(await decodeShareInput('')).toBeNull();
    expect(await decodeShareInput('LARPDOT1:not-valid-gzip')).toBeNull();

    const jsonWithWidget = JSON.stringify({
      type: PRESET_TYPE,
      version: 1,
      name: 'Widget Test',
      island: {
        ...sampleIsland,
        components: [
          { kind: 'WIDGET', height: 120 },
          { kind: 'NOTIFICATION_CONTENT', height: 96 },
        ],
      },
    });
    const decoded = await decodeShareInput(jsonWithWidget);
    expect(decoded?.islands[0].island.components).toHaveLength(1);
    expect(decoded?.islands[0].island.components[0].kind).toBe(
      'NOTIFICATION_CONTENT'
    );
  });

  it('formats known and unknown Android package names cleanly', () => {
    expect(formatAppDisplayName('com.google.android.apps.maps')).toBe(
      'Google Maps'
    );
    expect(formatAppDisplayName('org.example.cool_player')).toBe('Cool Player');
    expect(formatAppDisplayName(null)).toBeNull();
  });
});
