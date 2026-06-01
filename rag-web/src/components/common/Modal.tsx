import { ReactNode } from "react";
import { X } from "lucide-react";

type ModalProps = {
  title: string;
  description?: string;
  children: ReactNode;
  onClose: () => void;
};

export function Modal({ title, description, children, onClose }: ModalProps) {
  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal-card" role="dialog" aria-modal="true" aria-label={title}>
        <button type="button" className="modal-close" onClick={onClose} aria-label="关闭">
          <X size={20} />
        </button>
        <div className="modal-head">
          <h2>{title}</h2>
          {description ? <p>{description}</p> : null}
        </div>
        {children}
      </section>
    </div>
  );
}
