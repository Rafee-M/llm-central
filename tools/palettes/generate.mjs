// Development-time tool (NOT part of the app): computes app/src/main/assets/palettes.json.
//
// Why: Material You colours are computed from one "seed" colour with Google's Material Color
// Utilities (HCT colour space, tonal palettes). Shipping that library inside the APK would cost size,
// so we run it once here and ship only the results: 8 palettes × (light + dark) × 32 colour roles.
// On Android 12+ the "Material You" option uses the phone's own wallpaper colours instead.
//
// Run:  cd tools/palettes && npm install && npm run generate
// Library: https://github.com/material-foundation/material-color-utilities (version pinned in package.json)

import { writeFileSync } from 'node:fs';
import {
  Hct, SchemeTonalSpot, MaterialDynamicColors, argbFromHex, hexFromArgb,
} from '@material/material-color-utilities';

// The 8 palettes of PROJECT.md §4.4. Ids are stored in settings: never change them.
const PALETTES = [
  { id: 'duck', name: 'Duck Orange', seed: '#DE5833' },
  { id: 'ocean', name: 'Ocean', seed: '#1E6FD9' },
  { id: 'forest', name: 'Forest', seed: '#2E7D32' },
  { id: 'lavender', name: 'Lavender', seed: '#7E57C2' },
  { id: 'rose', name: 'Rose', seed: '#D81B60' },
  { id: 'teal', name: 'Teal', seed: '#00897B' },
  { id: 'amber', name: 'Amber', seed: '#F9A825' },
  { id: 'graphite', name: 'Graphite', seed: '#546E7A' },
];

// Colour roles, in this order in every array. theme/Scheme.java uses the same names.
const ROLES = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'error', 'onError', 'errorContainer', 'onErrorContainer',
  'background', 'onBackground', 'surface', 'onSurface', 'surfaceVariant', 'onSurfaceVariant',
  'surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer',
  'surfaceContainerHigh', 'surfaceContainerHighest',
  'inverseSurface', 'inverseOnSurface', 'outline', 'outlineVariant',
];

function roles(seedHex, isDark) {
  // "Tonal spot" is the scheme Android itself uses for wallpaper colours; contrast 0 = standard.
  const scheme = new SchemeTonalSpot(Hct.fromInt(argbFromHex(seedHex)), isDark, 0);
  return ROLES.map((role) => hexFromArgb(MaterialDynamicColors[role].getArgb(scheme)).toUpperCase());
}

const out = {
  generator: 'tools/palettes/generate.mjs (@material/material-color-utilities 0.3.0, SchemeTonalSpot, contrast 0)',
  roles: ROLES,
  palettes: PALETTES.map((p) => ({ ...p, light: roles(p.seed, false), dark: roles(p.seed, true) })),
};

const target = new URL('../../app/src/main/assets/palettes.json', import.meta.url);
writeFileSync(target, JSON.stringify(out) + '\n');
console.log(`wrote ${PALETTES.length} palettes × 2 × ${ROLES.length} roles to ${target.pathname}`);
