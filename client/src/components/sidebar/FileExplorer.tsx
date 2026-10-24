import React, { useState } from 'react';
import { useStudioStore } from '../../store/useStudioStore';
import { FileItem } from '../../types/studio';
import { Folder, FolderOpen, FileCode, ChevronRight, ChevronDown, AlertCircle } from 'lucide-react';

interface FileTreeItemProps {
  item: FileItem;
  level?: number;
}

const FileTreeItem: React.FC<FileTreeItemProps> = ({ item, level = 0 }) => {
  const [isOpen, setIsOpen] = useState(true);
  const { activeFileId, selectFile } = useStudioStore();
  const isSelected = activeFileId === item.id;

  if (item.type === 'directory') {
    return (
      <div>
        <div
          onClick={() => setIsOpen(!isOpen)}
          className="flex items-center gap-1.5 py-1 px-2 hover:bg-zinc-800/60 rounded cursor-pointer text-xs text-zinc-300 transition"
          style={{ paddingLeft: `${level * 12 + 8}px` }}
        >
          {isOpen ? <ChevronDown className="w-3.5 h-3.5 text-zinc-500" /> : <ChevronRight className="w-3.5 h-3.5 text-zinc-500" />}
          {isOpen ? <FolderOpen className="w-3.5 h-3.5 text-amber-500/80" /> : <Folder className="w-3.5 h-3.5 text-amber-500/80" />}
          <span className="truncate">{item.name}</span>
        </div>
        {isOpen && item.children && (
          <div>
            {item.children.map((child) => (
              <FileTreeItem key={child.id} item={child} level={level + 1} />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div
      onClick={() => selectFile(item.id)}
      className={`flex items-center justify-between py-1 px-2 rounded cursor-pointer text-xs transition ${
        isSelected
          ? 'bg-[#f37022]/15 text-[#f37022] dark:text-[#ff7b29] font-semibold border-l-2 border-[#f37022]'
          : 'text-zinc-400 hover:text-zinc-200 hover:bg-surface-elevated/60'
      }`}
      style={{ paddingLeft: `${level * 12 + 16}px` }}
    >
      <div className="flex items-center gap-1.5 truncate">
        <FileCode className={`w-3.5 h-3.5 ${isSelected ? 'text-[#f37022]' : 'text-zinc-400'}`} />
        <span className="truncate">{item.name}</span>
      </div>
      {item.hasIssues && (
        <span title="Содержит дефекты Big-O">
          <AlertCircle className="w-3 h-3 text-amber-400 shrink-0" />
        </span>
      )}
    </div>
  );
};

export const FileExplorer: React.FC = () => {
  const { files } = useStudioStore();

  return (
    <div className="p-2 space-y-0.5 select-none overflow-y-auto">
      {files.map((file) => (
        <FileTreeItem key={file.id} item={file} />
      ))}
    </div>
  );
};
