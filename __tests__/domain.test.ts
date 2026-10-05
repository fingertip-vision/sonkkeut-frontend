import {confirmation, isReadableElement, OrderFlow, parseOrder, screenReading} from '../src/domain';
import type {MenuItem} from '../src/domain';
import type {ScreenElement, ScreenStructure} from 'react-native-sonkkeut';
const menu: MenuItem[] = [
  {name: '아메리카노', aliases: ['아아'], sold_out: false},
  {name: '카페라떼', aliases: ['라떼'], sold_out: false},
  {name: '바닐라라떼', aliases: [], sold_out: false},
];
const el = (id: string, text: string, kind: ScreenElement['kind'] = 'button', conf = 0.95): ScreenElement => ({id, text, kind, conf, box: [0.1, 0.1, 0.4, 0.3]});
let kid = 0;
const screen = (screen_type: string, elements: ScreenElement[]): ScreenStructure => ({screen_type, keyframe_id: ++kid, elements});
const option = () => screen('option', [el('name', '아메리카노', 'menu'), el('hot', 'HOT'), el('ice', 'ICE'), el('add', '담기')]);
const menus = () => screen('menu', [el('coffee', '아메리카노', 'menu'), el('cart', '장바구니')]);
const success = {result: 'success' as const, reason: 'expected', speak: '눌렸습니다'};

test('Korean quantities, temperature, aliases and overlapping menu names', () => {
  expect(parseOrder('따뜻한 아메리카노 두 잔', menu).items[0]).toMatchObject({menu: '아메리카노', qty: 2, options: {temp: 'hot'}});
  expect(parseOrder('아아 한 잔', menu).items[0].options.temp).toBe('ice');
  expect(parseOrder('바닐라라떼 세 잔', menu).items).toHaveLength(1);
  expect(() => parseOrder('아메리카노 0잔', menu)).toThrow();
  expect(() => parseOrder('없는 메뉴', menu)).toThrow();
  expect(() => parseOrder('아메리카노', [{...menu[0], sold_out: true}])).toThrow('품절');
  expect(() => parseOrder('아메리카노 한 잔 샷 추가', menu)).toThrow('옵션');
  expect(() => parseOrder('아메리카노 한 잔 없는메뉴 한 잔', menu)).toThrow();
  const mixed = parseOrder('따뜻한 아메리카노 두 잔 아이스 카페라떼 한 잔', menu);
  expect(mixed.items.map(item => item.options.temp)).toEqual(['hot', 'ice']);
});

test('cart total mismatch prevents checkout', () => {
  const f = new OrderFlow();
  f.submit(parseOrder('아메리카노 두 잔', [{...menu[0], price: 4500}]));
  f.remaining = [0]; f.confirmed = true;
  f.acceptScreen({...screen('cart', [el('pay', '결제하기')]), total_price: 4500});
  expect(f.state).toBe('SE'); expect(f.action).toBeUndefined();
  f.acceptScreen({...screen('cart', [el('pay', '결제하기')]), total_price: 9000});
  expect(f.action?.role).toBe('checkout'); expect(f.message).toContain('9,000');
});

test('two coffees reach payment only after confirmed adds, including native event order', () => {
  const f = new OrderFlow();
  f.acceptScreen(menus()); f.submit(parseOrder('아이스 아메리카노 두 잔', menu));
  expect(f.plan()).toBeUndefined();
  expect(f.confirm()?.target.id).toBe('coffee');
  for (let i = 0; i < 2; i++) {
    f.press(); f.acceptScreen(option()); expect(f.state).toBe('S5'); f.verdict(success);
    expect(f.action?.target.id).toBe('ice');
    f.press(); f.acceptScreen(option()); f.verdict(success); expect(f.action?.role).toBe('add');
    f.press(); f.acceptScreen(menus()); f.verdict(success);
  }
  expect(f.remaining).toEqual([0]); expect(f.action?.target.id).toBe('cart');
  f.press(); f.acceptScreen(screen('cart', [el('pay', '결제하기')])); f.verdict(success);
  expect(f.action?.target.id).toBe('pay');
  f.press(); f.acceptScreen(screen('payment', [el('card', '카드를 넣어 주세요')])); f.verdict(success);
  expect(f.state).toBe('S6');
  f.acceptScreen(menus()); f.plan();
  expect(f.state).toBe('S6'); expect(f.action).toBeUndefined();
});

test('uncertain presses do not advance quantities; pause ignores verdicts', () => {
  const f = new OrderFlow(); f.acceptScreen(menus()); f.submit(parseOrder('아메리카노', menu)); f.confirm();
  f.press(); f.verdict({result: 'uncertain', reason: 'unexpected', speak: '다시 확인'});
  expect(f.state).toBe('SE'); expect(f.remaining).toEqual([1]);
  f.recover(); f.acceptScreen(menus()); f.press(); f.paused = true; f.verdict(success);
  expect(f.state).toBe('S5'); expect(f.remaining).toEqual([1]);
});

test('low confidence and unexpected payment fail closed', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아메리카노', menu));
  f.acceptScreen(screen('menu', [el('bad', '아메리카노', 'menu', 0.4)])); f.confirm();
  expect(f.action).toBeUndefined(); expect(f.state).toBe('SE');
  f.acceptScreen(screen('payment', [])); expect(f.state).toBe('SE');
});

test('wrong menu option is never added and reading order is spatial', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아메리카노', menu)); f.confirm();
  f.acceptScreen(screen('option', [el('latte', '카페라떼', 'menu'), el('add', '담기')]));
  expect(f.state).toBe('SE'); expect(f.action).toBeUndefined();
  const bottom = {...el('bottom', '아래'), box: [0.1, 0.8, 0.2, 0.9] as ScreenElement['box']};
  expect(screenReading(screen('menu', [bottom, el('top', '위')]))).toBe('위. 아래');
});

test('repeated menus preserve separate temperatures and spoken sizes are confirmed', () => {
  const order = parseOrder('아이스 아메리카노 라지 한 잔 그리고 따뜻한 아메리카노 두 잔', menu);
  expect(order.items.map(i => [i.menu, i.qty, i.options.temp, i.options.size])).toEqual([
    ['아메리카노', 1, 'ice', '라지'], ['아메리카노', 2, 'hot', undefined],
  ]);
  expect(confirmation(order)).toContain('라지');
  expect(parseOrder('아메리카노 ice 한 잔', menu).items[0].options.temp).toBe('ice');
  expect(parseOrder('두 잔 아메리카노', menu).items[0].qty).toBe(2);
  expect(parseOrder('아메리카노 그리고 두 잔 카페라떼', menu).items.map(i => i.qty)).toEqual([1, 2]);
  expect(parseOrder('아메리카노 한 잔 그리고 두 잔 아이스 카페라떼', menu).items.map(i => [i.qty, i.options.temp])).toEqual([[1, undefined], [2, 'ice']]);
  expect(() => parseOrder('아메리카노 0.5잔', menu)).toThrow('정수');
  expect(() => parseOrder('아메리카노 한 잔 두 잔', menu)).toThrow('수량');
  expect(() => parseOrder('아메리카노 라지 스몰 한 잔', menu)).toThrow('크기');
  const duplicateAlias = [menu[0], {...menu[1], aliases: ['아아']}];
  expect(() => parseOrder('아아 한 잔', duplicateAlias)).toThrow('별칭');
});

test('ambiguous screen labels, low confidence titles and invalid coordinates never become targets', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아메리카노', menu)); f.confirm();
  f.acceptScreen(screen('menu', [el('decaf', '디카페인 아메리카노', 'menu')]));
  expect(f.state).toBe('SE'); expect(f.action).toBeUndefined();
  f.acceptScreen(screen('menu', [{...el('bad-box', '아메리카노', 'menu'), box: [0.4, 0.1, 0.2, 0.3]}]));
  expect(f.action).toBeUndefined();
  f.acceptScreen(screen('menu', [el('coffee', '아메리카노 4,500원', 'menu')]));
  expect(f.action?.target.id).toBe('coffee');
  f.acceptScreen(screen('option', [el('name', '아메리카노', 'menu', 0.2), el('add', '담기')]));
  expect(f.state).toBe('SE'); expect(f.action).toBeUndefined();
  f.press(); expect(f.state).toBe('SE');
});

test('option selection must still match the order after a later screen update', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아이스 아메리카노 라지', menu)); f.confirm();
  const elements = [...option().elements, el('large', '라지')];
  f.acceptScreen({...screen('option', elements), selected: ['ice', 'large']});
  expect(f.action?.role).toBe('add');
  f.acceptScreen({...screen('option', elements), selected: ['hot', 'large']});
  expect(f.action?.target.id).toBe('ice');
  f.acceptScreen({...screen('option', elements), selected: ['ice', 'large']});
  expect(f.action?.role).toBe('add');
  f.acceptScreen({...screen('option', elements), selected: ['ice']});
  expect(f.action?.target.id).toBe('large');
  expect(f.action?.expect.selected).toBe('large');
});

test('interrupted adds reconcile an observed cart increment once and never repeat an unknown add', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아메리카노 두 잔', menu)); f.confirm();
  f.acceptScreen({...option(), cart_count: 0}); expect(f.action?.role).toBe('add'); f.press();
  f.paused = true; f.verdict(success); f.paused = false; f.recover();
  f.acceptScreen({...menus(), cart_count: 1});
  expect(f.remaining).toEqual([1]); expect(f.action?.role).toBe('menu');
  f.acceptScreen({...menus(), cart_count: 1}); expect(f.remaining).toEqual([1]);
  f.acceptScreen({...option(), cart_count: 1}); f.press(); f.recover();
  f.acceptScreen(menus());
  expect(f.remaining).toEqual([1]); expect(f.state).toBe('SE'); expect(f.action).toBeUndefined();
  f.acceptScreen({...menus(), cart_count: 3});
  expect(f.state).toBe('SE'); expect(f.remaining).toEqual([1]);
  f.recover(); f.acceptScreen({...menus(), cart_count: 2});
  expect(f.remaining).toEqual([0]); expect(f.action?.target.id).toBe('cart');
});

test('cart quantity mismatch blocks checkout even when prices are unavailable', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아메리카노 두 잔', menu)); f.confirmed = true; f.remaining = [0];
  f.acceptScreen({...screen('cart', [el('pay', '결제하기')]), cart_count: 3});
  expect(f.state).toBe('SE'); expect(f.action).toBeUndefined();
});

test('reset clears an old session and accepts restarted native keyframe numbering', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아메리카노', menu)); f.confirmed = true;
  f.acceptScreen({...option(), keyframe_id: 999}); f.press(); f.paused = true;
  f.reset();
  expect(f.state).toBe('S0'); expect(f.remaining).toEqual([]); expect(f.confirmed).toBe(false); expect(f.paused).toBe(false);
  f.acceptScreen({...menus(), keyframe_id: 1}); expect(f.state).toBe('S3');
  f.submit(parseOrder('아메리카노', menu)); expect(f.confirm()?.target.id).toBe('coffee');
});

test('uncertain custom OCR cannot guide a high-confidence detected button or announce its guessed price', () => {
  const coffee = {...el('coffee', '아메리카노', 'menu'), conf_ocr: 0.99, uncertain: true, price: 4500};
  const f = new OrderFlow();
  f.acceptScreen(screen('menu', [coffee])); f.submit(parseOrder('아메리카노', menu));
  expect(f.confirm()).toBeUndefined(); expect(f.action).toBeUndefined(); expect(f.state).toBe('SE');
  expect(screenReading(screen('menu', [coffee]))).toBe('읽기 불확실');
  expect(isReadableElement({...coffee, uncertain: false, conf_ocr: 0.4})).toBe(false);
});

test('read-only OCR titles cannot become checkout buttons', () => {
  const f = new OrderFlow(); f.submit(parseOrder('아메리카노', menu)); f.remaining = [0]; f.confirmed = true;
  f.acceptScreen({...screen('cart', [el('title', '결제하기', 'title')]), cart_count: 1});
  expect(f.action).toBeUndefined(); expect(f.state).toBe('SE');
});

test('the custom Whisper utterance preserves quantities and takeout through confirmation and method selection', () => {
  const intent = parseOrder('따뜻한 아메리카노 두 잔 하고 카페라떼 한 잔 포장해 주세요.', menu);
  expect(intent.items.map(i => [i.menu, i.qty])).toEqual([['아메리카노', 2], ['카페라떼', 1]]);
  expect(intent.items[0].options.temp).toBe('hot'); expect(intent.dine).toBe('포장');
  expect(confirmation(intent)).toContain('포장');
  const f = new OrderFlow(); f.acceptScreen(screen('method', [el('in', '매장'), el('out', '포장')])); f.submit(intent);
  expect(f.confirm()?.target.id).toBe('out'); expect(f.action?.expect.screen_type).toBe('menu');
  expect(() => parseOrder('아메리카노 한 잔 매장 포장', menu)).toThrow('하나만');
});
