/**
 * Адресация правок графа для POST /api/graph/mutate.
 * ID узла: `<methodId>/entry` (тело метода) или `<methodId>/s<путь>`, путь `3.b.1` — индексы с 1, между ними слоты.
 * Контейнер для вставки/переноса: `<methodId>/entry` или `<methodId>/s<путь>:<слот>`; index — с 0, вставка перед ним.
 */

export interface Placement {
  parentId: string;
  /** Позиция в контейнере, с 0 */
  index: number;
}

interface ParsedId {
  methodId: string;
  /** Токены пути: ['3', 'b', '1']; для entry — пусто */
  path: string[];
}

export function parseNodeId(nodeId: string): ParsedId | null {
  const slash = nodeId.lastIndexOf('/');
  if (slash < 0) return null;
  const methodId = nodeId.slice(0, slash);
  const tail = nodeId.slice(slash + 1);
  if (tail === 'entry') return { methodId, path: [] };
  if (!tail.startsWith('s')) return null;
  return { methodId, path: tail.slice(1).split('.') };
}

export function isEntryNode(nodeId: string): boolean {
  return parseNodeId(nodeId)?.path.length === 0;
}

/** Контейнер узла и его позиция в нём (с 0). Для entry — null. */
export function locationOf(nodeId: string): Placement | null {
  const p = parseNodeId(nodeId);
  if (!p || p.path.length === 0) return null;
  const index = Number(p.path[p.path.length - 1]) - 1;
  if (p.path.length === 1) return { parentId: `${p.methodId}/entry`, index };
  const owner = p.path.slice(0, -2).join('.');
  const slot = p.path[p.path.length - 2];
  return { parentId: `${p.methodId}/s${owner}:${slot}`, index };
}

/** ID узла, который окажется в контейнере на позиции index (после ответа сервера ID пересчитываются по позициям). */
export function nodeIdAt(place: Placement): string {
  const [base, slot] = place.parentId.split(':');
  if (base.endsWith('/entry')) return `${base.slice(0, -'/entry'.length)}/s${place.index + 1}`;
  return `${base}.${slot}.${place.index + 1}`;
}

/** Куда вставить новый блок, брошенный на узел target: сразу после него; на заголовок метода — в начало тела. */
export function placeAfter(targetId: string): Placement | null {
  if (isEntryNode(targetId)) return { parentId: targetId, index: 0 };
  const loc = locationOf(targetId);
  return loc ? { parentId: loc.parentId, index: loc.index + 1 } : null;
}

/** Первый слот составного узла: тело цикла и try — b, ветка then — t, первая ветка switch — s0. */
const FIRST_SLOT: Record<string, string> = { loop: 'b', try: 'b', condition: 't', switch: 's0' };

/** Начало тела составного узла (или метода). null — у узла нет тела. */
export function placeInside(targetId: string, targetType: string): Placement | null {
  if (isEntryNode(targetId)) return { parentId: targetId, index: 0 };
  const slot = FIRST_SLOT[targetType];
  return slot ? { parentId: `${targetId}:${slot}`, index: 0 } : null;
}

/**
 * Перенос узла nodeId сразу после targetId (REORDER). Сервер считает index уже без переносимого узла,
 * поэтому при переносе вниз в том же контейнере позиция сдвигается на 1.
 * null — перенос ничего не меняет или недопустим (метод целиком, внутрь самого себя).
 */
export function moveAfter(nodeId: string, targetId: string): Placement | null {
  if (nodeId === targetId || isEntryNode(nodeId)) return null;
  if (targetId.startsWith(nodeId + '.')) return null;
  const from = locationOf(nodeId);
  const to = placeAfter(targetId);
  if (!from || !to) return null;
  if (from.parentId === to.parentId) {
    const index = from.index < to.index ? to.index - 1 : to.index;
    return index === from.index ? null : { parentId: to.parentId, index };
  }
  return to;
}

export function moveInside(nodeId: string, containerId: string, containerType: string): Placement | null {
  if (nodeId === containerId || isEntryNode(nodeId) || containerId.startsWith(nodeId + '.')) return null;
  return placeInside(containerId, containerType);
}

/** Слот составного узла по ручке, из которой протянута связь */
const HANDLE_SLOT: Record<string, Record<string, string>> = {
  loop: { body: 'b' },
  condition: { true: 't', false: 'e' },
  try: { body: 'b', catch: 'c0', finally: 'f' },
  switch: { body: 's0' },
};

/**
 * Связь source → target на холсте как правка кода: target переносится туда, куда ведёт ручка.
 * Ручки тела (Тело, Да, catch, finally, case) — в начало этой ветки; «Нет» — в else, если он есть,
 * иначе сразу после if (туда и ведёт ветка «Нет»); обычный выход — сразу после source.
 */
export function connectPlacement(
  sourceId: string,
  sourceType: string,
  handle: string | null | undefined,
  targetId: string,
  conditionHasElse: boolean
): Placement | null {
  if (sourceId === targetId || isEntryNode(targetId) || sourceId.startsWith(targetId + '.')) return null;
  const slot = handle ? HANDLE_SLOT[sourceType]?.[handle] : undefined;
  if (slot && !(slot === 'e' && !conditionHasElse)) {
    if (isEntryNode(sourceId)) return null;
    return { parentId: `${sourceId}:${slot}`, index: 0 };
  }
  return moveAfter(targetId, sourceId);
}

/** Составные узлы, в тело которых можно бросить блок (с Shift) */
export const CONTAINER_TYPES = new Set(['method', 'loop', 'condition', 'try', 'switch']);

/** Имя, которого ещё нет в коде: value, value2, value3... */
function freshName(code: string, base: string): string {
  for (let k = 1; ; k++) {
    const name = k === 1 ? base : `${base}${k}`;
    if (!new RegExp(`\\b${name}\\b`).test(code)) return name;
  }
}

/**
 * Код блока из палитры. Сразу компилируемые заготовки (условие false, цикл на 0 итераций),
 * чтобы вставку можно было проверить компиляцией; `return;` в методе с результатом сервер отклонит.
 */
export function paletteSnippet(type: string, code: string): string | null {
  switch (type) {
    case 'var':
      return `int ${freshName(code, 'value')} = 0;`;
    case 'condition':
      return 'if (false) {\n}';
    case 'loop': {
      const i = freshName(code, 'i');
      return `for (int ${i} = 0; ${i} < 0; ${i}++) {\n}`;
    }
    case 'op':
    case 'call':
      return 'System.out.println();';
    case 'return':
      return 'return;';
    case 'method': {
      const m = freshName(code, 'customMethod');
      return `public void ${m}() {\n}`;
    }
    default:
      return null;
  }
}
