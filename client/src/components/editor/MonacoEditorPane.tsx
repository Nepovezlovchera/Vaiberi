import React, { useEffect, useRef } from 'react';
import Editor, { OnMount } from '@monaco-editor/react';
import { useStudioStore } from '../../store/useStudioStore';
import { EditorTabs } from './EditorTabs';

export const MonacoEditorPane: React.FC = () => {
  const { code, setCode, activeLineRange, nodes, selectNode, refreshGraphAndAnalysis } = useStudioStore();
  const editorRef = useRef<any>(null);
  const monacoRef = useRef<any>(null);
  const decorationsRef = useRef<string[]>([]);
  const debounceTimerRef = useRef<any>(null);

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

    // Двусторонняя синхронизация: курсор -> граф
    editor.onDidChangeCursorPosition((e) => {
      const currentLine = e.position.lineNumber;
      const targetNode = nodes.find(
        (n) =>
          n.data &&
          typeof n.data.lineStart === 'number' &&
          typeof n.data.lineEnd === 'number' &&
          (n.data.lineStart as number) <= currentLine &&
          currentLine <= (n.data.lineEnd as number)
      );

      if (targetNode) {
        selectNode(targetNode.id);
      }
    });
  };

  const handleCodeChange = (val: string | undefined) => {
    const newCode = val || '';
    setCode(newCode);

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }
    debounceTimerRef.current = setTimeout(() => {
      refreshGraphAndAnalysis(newCode);
    }, 600);
  };

  // Реакция на изменение activeLineRange (клик по узлу в графе или карточке дефекта)
  useEffect(() => {
    if (!editorRef.current || !monacoRef.current || !activeLineRange) return;

    const editor = editorRef.current;
    const monaco = monacoRef.current;

    editor.revealLineInCenter(activeLineRange.start);

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
    <div className="w-full h-full bg-[#18181b] flex flex-col overflow-hidden">
      <EditorTabs />
      <div className="flex-1 w-full h-[calc(100%-32px)] overflow-hidden">
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
    </div>
  );
};
