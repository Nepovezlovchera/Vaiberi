import React, { useState } from 'react';
import { TopBar } from './components/layout/TopBar';
import { LeftSidebar } from './components/sidebar/LeftSidebar';
import { FlowCanvas } from './components/canvas/FlowCanvas';
import { BottomTabsPane } from './components/terminal/BottomTabsPane';
import { AnalysisPanel } from './components/analysis/AnalysisPanel';

export const App: React.FC = () => {
  // Процент высоты для холста графа (по умолчанию 56%)
  const [canvasHeightPercent, setCanvasHeightPercent] = useState<number>(56);
  const [isDraggingSplit, setIsDraggingSplit] = useState<boolean>(false);

  const handleMouseDown = () => {
    setIsDraggingSplit(true);
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDraggingSplit) return;
    const containerHeight = window.innerHeight - 48; // вычитаем высоту TopBar
    const newPercent = Math.min(Math.max((e.clientY - 48) / containerHeight * 100, 25), 80);
    setCanvasHeightPercent(newPercent);
  };

  const handleMouseUp = () => {
    setIsDraggingSplit(false);
  };

  return (
    <div
      className="w-screen h-screen flex flex-col bg-background text-zinc-100 overflow-hidden"
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
    >
      {/* 1. Верхний Top-Bar */}
      <TopBar />

      {/* 2. Основная рабочая область из 3 колонок */}
      <div className="flex-1 w-full flex overflow-hidden">
        {/* Левая колонка: Проект + Палитра блоков */}
        <LeftSidebar />

        {/* Центральная колонка: Сплит Холст (Верх) / Табы Код & Консоль & Тесты (Низ) */}
        <div className="flex-1 flex flex-col min-w-0 h-full overflow-hidden relative">
          {/* Верх: Холст React Flow */}
          <div style={{ height: `${canvasHeightPercent}%` }} className="w-full relative">
            <FlowCanvas />
          </div>

          {/* Интерактивный разделитель высоты (Split Resizer) */}
          <div
            onMouseDown={handleMouseDown}
            className="h-1.5 w-full bg-border hover:bg-blue-500 cursor-row-resize transition-colors z-20 shrink-0 flex items-center justify-center group"
          >
            <div className="w-12 h-1 bg-zinc-600 group-hover:bg-blue-300 rounded-full" />
          </div>

          {/* Низ: Нижняя таб-панель (Monaco Editor, Консоль SSE, Тесты) */}
          <div style={{ height: `${100 - canvasHeightPercent}%` }} className="w-full">
            <BottomTabsPane />
          </div>
        </div>

        {/* Правая колонка: Анализ дефектов и Big-O метрики */}
        <AnalysisPanel />
      </div>
    </div>
  );
};

export default App;
