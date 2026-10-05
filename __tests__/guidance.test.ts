import {orderProgress, readableRows, targetOverlay, visualGuidance} from '../src/guidance';
import {parseOrder} from '../src/domain';
import {DEFAULT_MENU} from '../src/backend';
jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));

test('all native directions produce visual arrows and a nearby instruction', () => {
  const arrows = {right: '→', up_right: '↗', up: '↑', up_left: '↖', left: '←', down_left: '↙', down: '↓', down_right: '↘'} as const;
  for (const dir of Object.keys(arrows) as (keyof typeof arrows)[]) {
    const guide = visualGuidance('S4', {type: 'direction', dir, distance: 'near', target_id: 'a', vibe_hz: 6}, 'a', false, true);
    expect(guide.symbol).toBe(arrows[dir]); expect(guide.detail).toContain('거의 다');
  }
});
test('pause, screen loss and different targets cannot retain a press instruction', () => {
  const press = {type: 'press', target_id: 'a', vibe_hz: 10} as const;
  expect(visualGuidance('S4', press, 'a', false, true).title).toBe('지금 누르세요');
  for (const guide of [visualGuidance('S4', press, 'a', true, true), visualGuidance('S4', press, 'a', false, false), visualGuidance('S4', press, 'b', false, true)]) {
    expect(guide.title).not.toBe('지금 누르세요');
  }
  expect(visualGuidance('S4', {type: 'no_hand', vibe_hz: 0}, 'a', false, true).title).toContain('손끝');
  expect(visualGuidance('S6', press, undefined, false, true).title).toContain('결제');
});
test('reader displays spatial order and does not promote uncertain OCR to usable text', () => {
  const element = {kind: 'menu' as const, conf: 0.95, conf_ocr: 0.95};
  const rows = readableRows({screen_type: 'menu', keyframe_id: 2, elements: [
    {...element, id: 'bottom', text: '카페라떼', price: 5000, box: [0, 0.5, 0.4, 0.8]},
    {...element, id: 'top', text: '아메리카노', box: [0, 0, 0.4, 0.3]},
    {...element, id: 'bad', text: '잘못 읽은 버튼', uncertain: true, box: [0.5, 0, 0.9, 0.3]},
  ]});
  expect(rows.map(row => row.id)).toEqual(['top', 'bad', 'bottom']);
  expect(rows[1].text).not.toContain('잘못 읽은'); expect(rows[1].readable).toBe(false);
  expect(rows[2].text).toContain('5,000');
});
test('order progress counts confirmed additions rather than navigation or option actions', () => {
  const intent = parseOrder('아메리카노 두 잔 카페라떼 한 잔 포장', DEFAULT_MENU);
  expect(orderProgress(intent, [2, 1])).toEqual({total: 3, completed: 0, amount: 14000});
  expect(orderProgress(intent, [1, 0]).completed).toBe(2);
  expect(orderProgress(intent, [0, 0]).completed).toBe(3);
});
test('target highlight follows cover scaling and rejects invalid native coordinates', () => {
  expect(targetOverlay([100, 100, 200, 200], [400, 400], {width: 400, height: 200})).toEqual({left: 100, top: 0, width: 100, height: 100});
  expect(targetOverlay([NaN, 0, 100, 100], [400, 400], {width: 400, height: 200})).toBeUndefined();
  expect(targetOverlay([0, 0, 100, 100], [0, 0], {width: 400, height: 200})).toBeUndefined();
});
