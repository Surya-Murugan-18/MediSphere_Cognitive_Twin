import React, { useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { XIcon } from 'lucide-react';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  width?: string;
}

export function Modal({ open, onClose, title, description, children, footer, width = 'max-w-lg' }: ModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open &&
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label={title}>
          <motion.div
          className="absolute inset-0 bg-navy-950/40"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.16, ease: [0.23, 1, 0.32, 1] }}
          onClick={onClose} />
        
          <motion.div
          className={`relative w-full ${width} rounded-lg border border-slate-200 bg-white shadow-panel`}
          initial={{ opacity: 0, scale: 0.97, y: 8 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.97, y: 8 }}
          transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}>
          
            <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-4 py-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
                {description && <p className="mt-0.5 text-xs text-slate-500">{description}</p>}
              </div>
              <button
              type="button"
              onClick={onClose}
              aria-label="Close dialog"
              className="rounded p-1 text-slate-400 transition-colors duration-150 ease-out hover:bg-slate-100 hover:text-slate-600">
              
                <XIcon className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
            {children && <div className="px-4 py-4 text-sm text-slate-700">{children}</div>}
            {footer && <div className="flex justify-end gap-2 border-t border-slate-200 px-4 py-3">{footer}</div>}
          </motion.div>
        </div>
      }
    </AnimatePresence>);

}