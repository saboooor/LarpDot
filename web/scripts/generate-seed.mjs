#!/usr/bin/env node
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { gzipSync } from 'node:zlib';

const PRESET_TYPE = 'larpdot-custom-island-preset';
const BUNDLE_TYPE = 'larpdot-custom-island-bundle';
const PRESET_VERSION = 1;
const SHARE_CODE_PREFIX = 'LARPDOT1:';

function toShareCode(envelope) {
  const json = JSON.stringify(envelope);
  const compressed = gzipSync(Buffer.from(json, 'utf8'));
  const base64url = compressed
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
  return `${SHARE_CODE_PREFIX}${base64url}`;
}

function emptySlot(width, height) {
  return { width, height, paddingX: 8, paddingY: 8 };
}

function baseIsland(overrides) {
  return {
    name: 'Custom Island',
    visibility: 'IDLE',
    condition: 'MUSIC',
    tapAction: 'EXPAND',
    notificationRule: {
      package: '',
      channel: '',
      title: '',
      text: '',
      matchChannel: true,
    },
    components: [],
    LEFT: emptySlot(64, 24),
    RIGHT: emptySlot(64, 24),
    MAIN: emptySlot(300, 150),
    EXPANDED_LEFT: emptySlot(44, 44),
    EXPANDED_RIGHT: emptySlot(44, 44),
    ...overrides,
  };
}

const seedItems = [
  {
    slug: 'transit-live-commute-pack',
    name: 'Transit Live Commute Pack',
    author: 'LarpDot',
    description:
      'Two-island Transit bundle: live stops-remaining counter using capture groups plus a walk-to-stop countdown pill.',
    isBundle: true,
    appPackage: 'com.thetransitapp.droid',
    appName: 'Transit',
    visibility: 'CONDITIONAL',
    condition: 'NOTIFICATION',
    copies: 18,
    envelope: {
      type: BUNDLE_TYPE,
      version: PRESET_VERSION,
      name: 'Transit Live Commute Pack',
      description:
        'Live stops-remaining counter and walk-to-stop pill for Transit.',
      appPackage: 'com.thetransitapp.droid',
      appName: 'Transit',
      presets: [
        {
          name: 'Stops Remaining',
          description: 'Extracts stop count via capture group on right wing.',
          island: baseIsland({
            name: 'Stops Remaining',
            visibility: 'CONDITIONAL',
            condition: 'NOTIFICATION',
            tapAction: 'EXPAND',
            notificationRule: {
              package: 'com.thetransitapp.droid',
              channel: '',
              title: '',
              text: '',
              matchChannel: false,
              conditions: [
                {
                  field: 'TITLE',
                  operator: 'CONTAINS',
                  pattern: 'Get off in (?<stops>*) stop*',
                  join: 'AND',
                },
                {
                  field: 'TEXT',
                  operator: 'NOT_CONTAINS',
                  pattern: 'Arrived',
                  join: 'AND',
                },
              ],
            },
            LEFT: {
              ...emptySlot(44, 24),
              icon: 'NAVIGATION',
              iconColor: { mode: 'DOMINANT', rgb: 0x34d399 },
            },
            RIGHT: {
              ...emptySlot(72, 24),
              text: '{stops} stops',
              textStyle: {
                size: 12,
                rgb: 0xffffff,
                colorMode: 'CUSTOM',
                bold: true,
                italic: false,
                underline: false,
                alignment: 'CENTER',
              },
            },
            EXPANDED_LEFT: {
              ...emptySlot(44, 44),
              image: 'APP_ICON',
            },
            EXPANDED_RIGHT: {
              ...emptySlot(72, 44),
              text: '{stops} left',
              textStyle: {
                size: 13,
                rgb: 0x34d399,
                colorMode: 'DOMINANT',
                bold: true,
                italic: false,
                underline: false,
                alignment: 'END',
              },
            },
            components: [
              {
                kind: 'NOTIFICATION_CONTENT',
                height: 150,
              },
            ],
          }),
        },
        {
          name: 'Walk to Stop',
          description: 'Shows walking ETA before boarding.',
          island: baseIsland({
            name: 'Walk to Stop',
            visibility: 'CONDITIONAL',
            condition: 'NOTIFICATION',
            tapAction: 'OPEN_NOTIFICATION',
            notificationRule: {
              package: 'com.thetransitapp.droid',
              channel: '',
              title: '',
              text: '',
              matchChannel: false,
              conditions: [
                {
                  field: 'TITLE',
                  operator: 'CONTAINS',
                  pattern: 'Walk (*)',
                  join: 'AND',
                },
              ],
            },
            LEFT: {
              ...emptySlot(44, 24),
              image: 'NOTIFICATION_ICON',
              iconColor: { mode: 'DOMINANT', rgb: 0x34d399 },
            },
            RIGHT: {
              ...emptySlot(76, 24),
              text: '{1}',
              textStyle: {
                size: 12,
                rgb: 0xffffff,
                colorMode: 'CUSTOM',
                bold: true,
                italic: false,
                underline: false,
                alignment: 'CENTER',
              },
            },
            components: [
              {
                kind: 'TEXT',
                height: 54,
                text: '{title}\n{text}',
                textStyle: {
                  size: 14,
                  rgb: 0xffffff,
                  colorMode: 'CUSTOM',
                  bold: false,
                  italic: false,
                  underline: false,
                  alignment: 'START',
                },
              },
            ],
          }),
        },
      ],
    },
  },
  {
    slug: 'spotify-compact-album-bar',
    name: 'Spotify Compact Album Bar',
    author: 'LarpDot',
    description:
      'Clean compact Spotify pill with album art on the left, live track title on the right, and tactile playback buttons when expanded.',
    isBundle: false,
    appPackage: 'com.spotify.music',
    appName: 'Spotify',
    visibility: 'CONDITIONAL',
    condition: 'MUSIC',
    copies: 31,
    envelope: {
      type: PRESET_TYPE,
      version: PRESET_VERSION,
      name: 'Spotify Compact Album Bar',
      description:
        'Album art + track title compact pill with Material 3 expanded controls.',
      island: baseIsland({
        name: 'Spotify Compact Album Bar',
        visibility: 'CONDITIONAL',
        condition: 'MUSIC',
        tapAction: 'EXPAND',
        tapAppPackage: 'com.spotify.music',
        LEFT: {
          ...emptySlot(36, 24),
          image: 'ALBUM_ART',
        },
        RIGHT: {
          ...emptySlot(96, 24),
          text: '{title}',
          textStyle: {
            size: 12,
            rgb: 0xffffff,
            colorMode: 'CUSTOM',
            bold: true,
            italic: false,
            underline: false,
            alignment: 'CENTER',
          },
        },
        EXPANDED_LEFT: {
          ...emptySlot(48, 48),
          image: 'ALBUM_ART',
        },
        EXPANDED_RIGHT: {
          ...emptySlot(44, 44),
          icon: 'WAVEFORM',
          iconColor: { mode: 'DOMINANT', rgb: 0x1db954 },
        },
        components: [
          {
            kind: 'TEXT',
            height: 48,
            text: '{title} — {artist}',
            textStyle: {
              size: 14,
              rgb: 0xffffff,
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
              radius: 22,
              connected: true,
            },
          },
        ],
      }),
    },
  },
  {
    slug: 'google-maps-turn-glance',
    name: 'Google Maps Turn Glance',
    author: 'LarpDot',
    description:
      'Glanceable turn-by-turn pill using Maps app color on the left wing and live maneuver text on the right.',
    isBundle: false,
    appPackage: 'com.google.android.apps.maps',
    appName: 'Google Maps',
    visibility: 'CONDITIONAL',
    condition: 'LIVE_ACTIVITY',
    copies: 24,
    envelope: {
      type: PRESET_TYPE,
      version: PRESET_VERSION,
      name: 'Google Maps Turn Glance',
      description: 'Navigation icon + live maneuver text for Google Maps.',
      island: baseIsland({
        name: 'Google Maps Turn Glance',
        visibility: 'CONDITIONAL',
        condition: 'LIVE_ACTIVITY',
        activityKind: 'NAVIGATION',
        tapAction: 'OPEN_APP',
        tapAppPackage: 'com.google.android.apps.maps',
        LEFT: {
          ...emptySlot(40, 24),
          icon: 'NAVIGATION',
          iconColor: { mode: 'DOMINANT', rgb: 0x0f9d58 },
        },
        RIGHT: {
          ...emptySlot(112, 24),
          text: '{title}',
          textStyle: {
            size: 12,
            rgb: 0xffffff,
            colorMode: 'CUSTOM',
            bold: true,
            italic: false,
            underline: false,
            alignment: 'START',
          },
        },
        EXPANDED_LEFT: {
          ...emptySlot(44, 44),
          icon: 'NAVIGATION',
          iconColor: { mode: 'DOMINANT', rgb: 0x0f9d58 },
        },
        components: [
          {
            kind: 'TEXT',
            height: 60,
            text: '{title}\n{text}',
            textStyle: {
              size: 15,
              rgb: 0xffffff,
              colorMode: 'CUSTOM',
              bold: true,
              italic: false,
              underline: false,
              alignment: 'START',
            },
          },
        ],
      }),
    },
  },
  {
    slug: 'swipe-down-quick-now-playing',
    name: 'Swipe-Down Now Playing Card',
    author: 'LarpDot',
    description:
      'Swipe down from your camera cutout anytime to reveal album art, track details, and quick playback controls.',
    isBundle: false,
    appPackage: null,
    appName: null,
    visibility: 'SWIPE_DOWN',
    condition: 'MUSIC',
    copies: 14,
    envelope: {
      type: PRESET_TYPE,
      version: PRESET_VERSION,
      name: 'Swipe-Down Now Playing Card',
      description: 'Cutout swipe-down mini player with album art and controls.',
      island: baseIsland({
        name: 'Swipe-Down Now Playing Card',
        visibility: 'SWIPE_DOWN',
        condition: 'MUSIC',
        tapAction: 'EXPAND',
        EXPANDED_LEFT: {
          ...emptySlot(44, 44),
          image: 'ALBUM_ART',
        },
        EXPANDED_RIGHT: {
          ...emptySlot(44, 44),
          icon: 'MUSIC',
          iconColor: { mode: 'MATERIAL_YOU', rgb: 0xffffff },
        },
        components: [
          {
            kind: 'TEXT',
            height: 44,
            text: '{title} · {artist}',
            textStyle: {
              size: 14,
              rgb: 0xffffff,
              colorMode: 'CUSTOM',
              bold: true,
              italic: false,
              underline: false,
              alignment: 'CENTER',
            },
          },
          {
            kind: 'BUTTONS',
            height: 54,
            buttonStyle: {
              playBackground: true,
              playAccent: true,
              skipBackground: false,
              radius: 20,
              connected: true,
            },
          },
        ],
      }),
    },
  },
];

function sqlString(val) {
  if (val === null || val === undefined) return 'NULL';
  return `'${String(val).replace(/'/g, "''")}'`;
}

const statements = [
  '-- Starter community presets for LarpDot (idempotent INSERT OR IGNORE)',
];

const baseTs = Math.floor(Date.now() / 1000) - 3600 * 12;
seedItems.forEach((item, idx) => {
  const shareCode = toShareCode(item.envelope);
  const payloadJson = JSON.stringify(item.envelope);
  const islandCount = item.isBundle ? item.envelope.presets.length : 1;
  const createdAt = baseTs + idx * 1800;

  statements.push(
    `INSERT OR IGNORE INTO presets (slug, name, author, description, is_bundle, app_package, app_name, island_count, visibility, condition, payload, share_code, copies, created_at) VALUES (${[
      sqlString(item.slug),
      sqlString(item.name),
      sqlString(item.author),
      sqlString(item.description),
      item.isBundle ? 1 : 0,
      sqlString(item.appPackage),
      sqlString(item.appName),
      islandCount,
      sqlString(item.visibility),
      sqlString(item.condition),
      sqlString(payloadJson),
      sqlString(shareCode),
      item.copies,
      createdAt,
    ].join(', ')});`
  );
});

const outPath = resolve(import.meta.dirname, '..', 'drizzle', 'seed.sql');
writeFileSync(outPath, `${statements.join('\n')}\n`, 'utf8');
console.log(`Generated ${outPath} with ${seedItems.length} starter presets.`);
