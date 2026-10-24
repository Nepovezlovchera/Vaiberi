import React, { useEffect, useRef } from 'react';
import Editor, { OnMount } from '@monaco-editor/react';
import { useStudioStore } from '../../store/useStudioStore';
import { EditorTabs } from './EditorTabs';

export const MonacoEditorPane: React.FC = () => {
  const { code, setCode, activeLineRange, theme } = useStudioStore();
  const editorRef = useRef<any>(null);
  const monacoRef = useRef<any>(null);
  const decorationsRef = useRef<string[]>([]);
  const cursorListenerRef = useRef<{ dispose: () => void } | null>(null);
  // Выделение пришло из курсора редактора — не перескакиваем на узел
  const fromCursorRef = useRef(false);

  const defineThemes = (monaco: any) => {
    monaco.editor.defineTheme('axenix-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: 'f37022', fontStyle: 'bold' },
        { token: 'type', foreground: 'c4a4e8' },
        { token: 'string', foreground: '10b981' },
        { token: 'comment', foreground: '6b6370', fontStyle: 'italic' },
        { token: 'number', foreground: 'ff6500' },
      ],
      colors: {
        'editor.background': '#120e16',
        'editor.foreground': '#fdfcfb',
        'editor.lineHighlightBackground': '#1a1520',
        'editorCursor.foreground': '#f37022',
        'editorLineNumber.foreground': '#5b5263',
        'editorLineNumber.activeForeground': '#f37022',
        'editor.selectionBackground': '#5f308c44',
      },
    });

    monaco.editor.defineTheme('axenix-light', {
      base: 'vs',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: 'd35d25', fontStyle: 'bold' },
        { token: 'type', foreground: '5f308c' },
        { token: 'string', foreground: '047857' },
        { token: 'comment', foreground: '8e8494', fontStyle: 'italic' },
        { token: 'number', foreground: 'c5621b' },
      ],
      colors: {
        'editor.background': '#ffffff',
        'editor.foreground': '#080407',
        'editor.lineHighlightBackground': '#f7f6f4',
        'editorCursor.foreground': '#f37022',
        'editorLineNumber.foreground': '#968e9b',
        'editorLineNumber.activeForeground': '#f37022',
        'editor.selectionBackground': '#5f308c22',
      },
    });
  };

  const handleEditorMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;
    monacoRef.current = monaco;

    defineThemes(monaco);
    monaco.editor.setTheme(theme === 'dark' ? 'axenix-dark' : 'axenix-light');

    // Стилизация и настройки редактора
    editor.updateOptions({
      minimap: { enabled: false },
      fontSize: 13,
      lineHeight: 20,
      fontFamily: '"JetBrains Mono", Consolas, monospace',
      scrollBeyondLastLine: false,
      renderLineHighlight: 'all',
      smoothScrolling: true,
      cursorBlinking: 'smooth',
      padding: { top: 12, bottom: 12 },
    });

    // Двусторонняя синхронизация: курсор -> граф.
    // Узлы читаем из стора в момент события: граф меняется после загрузки с бэкенда.
    cursorListenerRef.current?.dispose();
    cursorListenerRef.current = editor.onDidChangeCursorPosition((e: any) => {
      const currentLine = e.position.lineNumber;
      const { nodes: currentNodes, selectedNodeId, selectNode: select } = useStudioStore.getState();

      let best: { id: string; type?: string; span: number } | null = null;
      for (const n of currentNodes) {
        const start = n.data?.lineStart;
        const end = n.data?.lineEnd;
        if (typeof start !== 'number' || typeof end !== 'number') continue;
        if (start > currentLine || currentLine > end) continue;
        const span = end - start;
        const better =
          !best ||
          (best.type === 'method' && n.type !== 'method') ||
          (span < best.span && !(n.type === 'method' && best.type !== 'method'));
        if (better) best = { id: n.id, type: n.type, span };
      }

      if (best && best.id !== selectedNodeId) {
        fromCursorRef.current = true;
        select(best.id);
      }
    });
  };

  // Пересборка графа с задержкой запускается в сторе (setCode), там же отбрасываются устаревшие ответы
  const handleCodeChange = (val: string | undefined) => setCode(val || '');

  useEffect(() => {
    return () => {
      cursorListenerRef.current?.dispose();
      cursorListenerRef.current = null;
    };
  }, []);

  // Реакция на изменение activeLineRange (клик по узлу в графе или карточке дефекта)
  useEffect(() => {
    if (!editorRef.current || !monacoRef.current || !activeLineRange) return;

    const editor = editorRef.current;
    const monaco = monacoRef.current;

    if (fromCursorRef.current) {
      fromCursorRef.current = false;
    } else {
      editor.revealLineInCenter(activeLineRange.start);
    }

    // Подсветка диапазона строк
    decorationsRef.current = editor.deltaDecorations(decorationsRef.current, [
      {
        range: new monaco.Range(activeLineRange.start, 1, activeLineRange.end, 100),
        options: {
          isWholeLine: true,
          className: 'highlighted-ast-line',
          glyphMarginClassName: 'text-amber-400',
        },
      },
    ]);
  }, [activeLineRange]);

  // Реакция на переключение темы
  useEffect(() => {
    if (monacoRef.current) {
      monacoRef.current.editor.setTheme(theme === 'dark' ? 'axenix-dark' : 'axenix-light');
    }
  }, [theme]);

  return (
    <div className="w-full h-full bg-surface flex flex-col overflow-hidden">
      <EditorTabs />
      <div className="flex-1 w-full h-[calc(100%-32px)] overflow-hidden">
        <Editor
          height="100%"
          defaultLanguage="java"
          theme={theme === 'dark' ? 'axenix-dark' : 'axenix-light'}
          value={code}
          onChange={handleCodeChange}
          onMount={handleEditorMount}
          options={{
            automaticLayout: true,
            tabSize: 4,
            formatOnType: true,
          }}
        />
      </div>
    </div>
  );
};
