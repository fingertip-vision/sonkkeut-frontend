import type {GuidanceEvent, ScreenStructure} from 'react-native-sonkkeut';
import type {AppState, Intent} from './domain';
import {isReadableElement} from './domain';

const directions = {
  right: ['→', '오른쪽'], up_right: ['↗', '오른쪽 위'], up: ['↑', '위쪽'], up_left: ['↖', '왼쪽 위'],
  left: ['←', '왼쪽'], down_left: ['↙', '왼쪽 아래'], down: ['↓', '아래쪽'], down_right: ['↘', '오른쪽 아래'],
};

export function visualGuidance(state: AppState, event: GuidanceEvent | undefined, targetId: string | undefined, paused: boolean, found: boolean) {
  if (paused) {return {symbol: 'Ⅱ', title: '안내가 멈췄어요', detail: '안내 계속을 누르면 화면을 다시 확인합니다'};}
  if (state === 'S6') {return {symbol: '✓', title: '결제 화면에 도착했어요', detail: '키오스크에서 주문 내역을 확인하고 결제해 주세요'};}
  if (state === 'S5') {return {symbol: '●', title: '지금 누르세요', detail: '누른 뒤에는 손을 멈춰 주세요 · 화면 반응 확인 중'};}
  if (state === 'SE') {return {symbol: '!', title: '다시 확인이 필요해요', detail: '손을 멈추고 안내 계속을 눌러 주세요'};}
  if (!found) {return {symbol: '▣', title: '키오스크 화면을 비춰 주세요', detail: '화면 전체가 보이도록 휴대폰 각도를 조절해 주세요'};}
  if (state !== 'S4' || !targetId || !event || (event.target_id && event.target_id !== targetId)) {
    return {symbol: '◎', title: state === 'S3' ? '주문을 입력하고 확인해 주세요' : '화면을 확인하고 있어요', detail: '안내 문장이 아래에 함께 표시됩니다'};
  }
  if (event.type === 'no_hand') {return {symbol: '☝', title: '손끝을 보여 주세요', detail: '키오스크 화면 앞에서 손을 들어 주세요'};}
  if (event.type === 'point') {return {symbol: '☝', title: '검지만 펴 주세요', detail: '다른 손가락은 접고 검지 끝을 보여 주세요'};}
  if (event.type === 'hold' || event.type === 'reset') {return {symbol: 'Ⅱ', title: '잠시 멈춰 주세요', detail: event.speak || '화면과 손끝을 다시 확인하고 있습니다'};}
  if (event.type === 'press' && event.target_id === targetId) {return {symbol: '●', title: '지금 누르세요', detail: '목표 버튼 위에 손끝이 도착했어요'};}
  if (event.type === 'direction' && event.dir) {
    const [symbol, title] = directions[event.dir];
    return {symbol, title: `${title}으로 이동`, detail: event.distance === 'reach' ? '목표 버튼에 도착했어요. 다음 안내를 기다려 주세요' : event.distance === 'near' ? '거의 다 왔어요 · 조금씩 이동해 주세요' : '검지를 천천히 이동해 주세요'};
  }
  return {symbol: '◎', title: '손끝 위치를 확인하고 있어요', detail: '다음 안내를 기다려 주세요'};
}

export function readableRows(screen?: ScreenStructure) {
  return [...(screen?.elements ?? [])].sort((a, b) => Math.abs(a.box[1] - b.box[1]) > 0.03 ? a.box[1] - b.box[1] : a.box[0] - b.box[0])
    .map(element => ({id: element.id, readable: isReadableElement(element) && !!element.text?.trim(),
      text: isReadableElement(element) && element.text?.trim() ? `${element.text}${element.price != null && !element.text.includes(`${element.price}`) && !element.text.includes(element.price.toLocaleString()) ? ` · ${element.price.toLocaleString()}원` : ''}` : '읽기 불확실 · 화면 각도를 바꿔 주세요'}));
}

export function orderProgress(intent: Intent | undefined, remaining: number[]) {
  const total = intent?.items.reduce((sum, item) => sum + item.qty, 0) ?? 0;
  const left = remaining.reduce((sum, n) => sum + n, 0);
  const amount = intent?.items.every(item => item.price != null) ? intent.items.reduce((sum, item) => sum + item.price! * item.qty, 0) : undefined;
  return {total, completed: Math.max(0, total - left), amount};
}

// Native target coordinates are pixels after rotation; contain preserves every frame edge.
export function targetOverlay(box: number[] | null | undefined, frame: number[] | undefined, preview: {width: number; height: number}) {
  if (!box || box.length !== 4 || !frame || frame.length !== 2 || ![...box, ...frame, preview.width, preview.height].every(Number.isFinite)) {return undefined;}
  const [fw, fh] = frame;
  const [x1, y1, x2, y2] = box;
  if (fw <= 0 || fh <= 0 || preview.width <= 0 || preview.height <= 0 || x2 <= x1 || y2 <= y1) {return undefined;}
  const scale = Math.min(preview.width / fw, preview.height / fh);
  return {left: x1 * scale + (preview.width - fw * scale) / 2, top: y1 * scale + (preview.height - fh * scale) / 2, width: (x2 - x1) * scale, height: (y2 - y1) * scale};
}
