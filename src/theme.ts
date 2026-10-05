import {createContext, useContext} from 'react';
import {StyleSheet} from 'react-native';

export type ThemeName = 'dark' | 'light';
export const palettes = {
  dark: {background: '#080f1e', surface: '#142238', ink: '#f5f8ff', muted: '#c9d4e7', accent: '#ffdf38', buttonText: '#111827', line: '#9baac2', disabled: '#33455f', disabledText: '#f5f8ff'},
  light: {background: '#f5f7fb', surface: '#ffffff', ink: '#111827', muted: '#425269', accent: '#ffdf38', buttonText: '#111827', line: '#52637c', disabled: '#e1e7ef', disabledText: '#34435b'},
};

export function createTheme(name: ThemeName) {
  const colors = palettes[name];
  const styles = StyleSheet.create({
    root: {flex: 1, backgroundColor: colors.background}, flex: {flex: 1},
    header: {minHeight: 68, paddingHorizontal: 18, paddingVertical: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12},
    title: {fontSize: 26, fontWeight: '800', color: colors.ink}, headerButton: {minHeight: 48, borderWidth: 2, borderColor: colors.line, borderRadius: 12, paddingHorizontal: 14, justifyContent: 'center'}, headerButtonText: {fontSize: 18, fontWeight: '700', color: colors.ink},
    connection: {paddingHorizontal: 18, paddingBottom: 8}, connectionText: {fontSize: 16, color: colors.muted},
    main: {flex: 1, paddingHorizontal: 14, gap: 10, minHeight: 0},
    preview: {flex: 1, minHeight: 0, overflow: 'hidden', borderRadius: 16, backgroundColor: '#000000', borderWidth: 2, borderColor: colors.line},
    placeholder: {flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14, padding: 20, backgroundColor: colors.surface},
    placeholderTitle: {fontSize: 28, fontWeight: '800', color: colors.ink, textAlign: 'center'}, placeholderBody: {fontSize: 19, lineHeight: 28, color: colors.muted, textAlign: 'center'},
    portraitFrame: {width: 94, height: 166, borderWidth: 4, borderColor: colors.accent, borderRadius: 12, padding: 12, justifyContent: 'flex-end'}, frameLine: {height: 8, borderRadius: 4, backgroundColor: colors.accent, marginTop: 10},
    outline: {position: 'absolute', borderWidth: 5, borderColor: '#ffdf38', borderRadius: 6},
    previewBadge: {position: 'absolute', bottom: 0, left: 0, right: 0, backgroundColor: '#080f1e', padding: 8}, previewBadgeText: {color: '#f5f8ff', fontSize: 16, textAlign: 'center'},
    guidance: {backgroundColor: colors.surface, borderRadius: 16, padding: 14, gap: 8}, guideHeading: {flexDirection: 'row', alignItems: 'center', gap: 12},
    directionBox: {width: 54, height: 54, borderRadius: 12, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center'}, direction: {fontSize: 36, color: colors.buttonText, fontWeight: '800'},
    guideText: {flex: 1}, guideTitle: {fontSize: 24, lineHeight: 32, fontWeight: '800', color: colors.ink}, caption: {fontSize: 18, lineHeight: 27, color: colors.muted},
    footer: {flexDirection: 'row', gap: 10, padding: 14, backgroundColor: colors.background},
    button: {minHeight: 68, borderRadius: 14, paddingHorizontal: 16, paddingVertical: 16, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.accent, borderWidth: 2, borderColor: name === 'light' ? colors.ink : colors.accent},
    buttonText: {fontSize: 22, lineHeight: 30, fontWeight: '800', color: colors.buttonText, textAlign: 'center'}, secondaryButton: {backgroundColor: colors.surface, borderColor: colors.line}, secondaryText: {color: colors.ink},
    disabled: {backgroundColor: colors.disabled, borderColor: colors.disabled}, disabledText: {color: colors.disabledText}, pressed: {borderColor: colors.ink},
    content: {padding: 18, gap: 18, paddingBottom: 28}, sectionTitle: {fontSize: 27, lineHeight: 36, fontWeight: '800', color: colors.ink}, body: {fontSize: 21, lineHeight: 30, color: colors.ink}, small: {fontSize: 18, lineHeight: 27, color: colors.muted},
    card: {padding: 18, gap: 16, backgroundColor: colors.surface, borderRadius: 16, borderWidth: 1, borderColor: colors.line}, row: {flexDirection: 'row', gap: 14, alignItems: 'center', justifyContent: 'space-between'},
    input: {minHeight: 64, borderWidth: 2, borderColor: colors.line, borderRadius: 12, padding: 14, color: colors.ink, backgroundColor: colors.background, fontSize: 21, lineHeight: 30},
    choice: {minHeight: 66, borderWidth: 2, borderColor: colors.line, borderRadius: 12, padding: 16, backgroundColor: colors.surface}, choiceSelected: {backgroundColor: colors.accent, borderColor: name === 'light' ? colors.ink : colors.accent}, choiceText: {fontSize: 21, color: colors.ink, fontWeight: '700'}, choiceSelectedText: {color: colors.buttonText},
    menuRow: {gap: 4, paddingVertical: 10, borderBottomWidth: 1, borderColor: colors.line}, warning: {borderWidth: 3, borderColor: name === 'dark' ? colors.accent : colors.line},
  });
  return {colors, styles, name};
}
export const ThemeContext = createContext(createTheme('dark'));
export const useTheme = () => useContext(ThemeContext);
