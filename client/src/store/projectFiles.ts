import { FileItem, FileTab } from '../types/studio';
import { ProjectFileDto } from '../api/apiClient';

export const flattenFiles = (items: FileItem[]): FileItem[] =>
  items.flatMap(item => item.type === 'file' ? [item] : flattenFiles(item.children ?? []));

export const sourcePath = (path: string) => path.replace(/\\/g, '/').replace(/^src\//, '');

export function projectFiles(items: FileItem[], activeId: string, code: string): ProjectFileDto[] {
  return flattenFiles(items).filter(f => f.name.endsWith('.java')).map(f => ({
    path: sourcePath(f.path), source: f.id === activeId ? code : f.code ?? '',
  }));
}

/** Keep server paths intact, including files with the same name in different packages. */
export function projectView(files: ProjectFileDto[], preferredPath?: string) {
  if (!files.length) throw new Error('Сервер вернул пустой проект');
  const items: FileItem[] = files.map(f => ({
    id: `file:${f.path}`, name: f.path.split('/').pop()!, path: `src/${f.path}`,
    type: 'file', code: f.source,
  }));
  const active = items.find(f => sourcePath(f.path) === preferredPath)
    ?? items.find(f => /static\s+void\s+main\s*\(/.test(f.code ?? '')) ?? items[0];
  const tabs: FileTab[] = items.map(f => ({ id: `tab:${f.path}`, name: f.name, path: f.path, code: f.code ?? '' }));
  return {
    files: [{ id: 'project-src', name: 'src', path: 'src', type: 'directory' as const, children: items }],
    activeFileId: active.id, code: active.code ?? '', openTabs: tabs,
    activeTabId: `tab:${active.path}`,
  };
}
