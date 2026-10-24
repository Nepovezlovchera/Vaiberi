import React, { useState } from 'react';
import { TopBar } from './components/layout/TopBar';
import { LeftSidebar } from './components/sidebar/LeftSidebar';
import { FlowCanvas } from './components/canvas/FlowCanvas';
import { BottomTabsPane } from './components/terminal/BottomTabsPane';
import { AnalysisPanel } from './components/analysis/AnalysisPanel';
import { useIsMobile } from './hooks/useIsMobile';
import { FolderTree, Workflow, Code, Activity } from 'lucide-react';

type MobileView = 'project' | 'canvas' | 'code' | 'analysis';

const MOBILE_TABS: { id: MobileView; label: string; icon: React.FC<{ className?: string }> }[] = [
  { id: 'project', label: 'Проект', icon: FolderTree },
  { id: 'canvas', label: 'Граф', icon: Workflow },
  { id: 'code', label: 'Код', icon: Code },
  { id: 'analysis', label: 'Анализ', icon: Activity },
];

export const App: React.FC = () => {
  // Процент высоты для холста графа (по умолчанию 68% для крупной схемы при запуске)
  const [canvasHeightPercent, setCanvasHeightPercent] = useState<number>(68);
  const [isDraggingSplit, setIsDraggingSplit] = useState<boolean>(false);
  const isMobile = useIsMobile();
  const [mobileView, setMobileView] = useState<MobileView>('canvas');

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

  // Мобильная раскладка: один раздел на весь экран + нижняя навигация
  if (isMobile) {
    return (
      <div className="w-screen h-[100dvh] flex flex-col bg-background text-zinc-100 overflow-hidden">
        <TopBar />

        <main className="flex-1 min-h-0 w-full overflow-hidden relative">
          {mobileView === 'project' && <LeftSidebar />}
          {mobileView === 'canvas' && <FlowCanvas />}
          {mobileView === 'code' && <BottomTabsPane />}
          {mobileView === 'analysis' && <AnalysisPanel />}
        </main>

        <nav className="shrink-0 grid grid-cols-4 bg-surface border-t border-border pb-[env(safe-area-inset-bottom)]">
          {MOBILE_TABS.map(({ id, label, icon: Icon }) => {
            const isActive = mobileView === id;
            return (
              <button
                key={id}
                onClick={() => setMobileView(id)}
                className={`flex flex-col items-center justify-center gap-0.5 py-2 text-[11px] font-medium transition border-t-2 ${
                  isActive
                    ? 'text-[#f37022] border-[#f37022] bg-surface-elevated'
                    : 'text-zinc-400 border-transparent active:bg-surface-elevated'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span>{label}</span>
              </button>
            );
          })}
        </nav>
      </div>
    );
  }

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
            className="h-1.5 w-full bg-border hover:bg-[#f37022] cursor-row-resize transition-colors z-20 shrink-0 flex items-center justify-center group"
          >
            <div className="w-12 h-1 bg-zinc-500 group-hover:bg-white rounded-full transition-colors" />
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
