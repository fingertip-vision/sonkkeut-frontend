import {palettes} from '../src/theme';

function luminance(hex: string) {
  const channels = [1, 3, 5].map(index => parseInt(hex.slice(index, index + 2), 16) / 255)
    .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
}
function ratio(a: string, b: string) {
  const x = luminance(a), y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}
test.each(['dark', 'light'] as const)('%s theme has at least 4.5:1 contrast for every app text role', name => {
  const c = palettes[name];
  for (const [text, background] of [[c.ink, c.background], [c.ink, c.surface], [c.muted, c.background], [c.muted, c.surface], [c.buttonText, c.accent], [c.disabledText, c.disabled]]) {
    expect(ratio(text, background)).toBeGreaterThanOrEqual(4.5);
  }
  expect(ratio(c.line, c.surface)).toBeGreaterThanOrEqual(3);
  expect(ratio(c.line, c.background)).toBeGreaterThanOrEqual(3);
});
