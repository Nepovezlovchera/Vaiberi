import React, { useEffect, useRef } from 'react';
import Editor, { OnMount } from '@monaco-editor/react';
import { useStudioStore } from '../../store/useStudioStore';

export const MonacoEditorPane: React.FC = () => {
  const { code, setCode, activeLineRange } = useStudioStore();
  const editorRef = useRef<any>(null);
  const monacoRef = useRef<any>(null);
  const decorationsRef = useRef<string[]>([]);
  const cursorListenerRef = useRef<{ dispose: () => void } | null>(null);
  // Выделение пришло из курсора редактора — не перескакиваем на узел
  const fromCursorRef = useRef(false);

  const handleEditorMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;
    monacoRef.current = monaco;

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

  return (
    <div className="w-full h-full bg-[#18181b] overflow-hidden">
      <Editor
        height="100%"
        defaultLanguage="java"
        theme="vs-dark"
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
  );
};
