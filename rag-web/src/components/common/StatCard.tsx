import { ReactNode } from "react";

type StatCardProps = {
  icon: ReactNode;
  label: string;
  value: number | string;
  hint?: string;
};

export function StatCard({ icon, label, value, hint = "实时" }: StatCardProps) {
  return (
    <div className="stat-card">
      <span className="stat-card-icon">{icon}</span>
      <div className="stat-card-content">
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
      <em>{hint}</em>
    </div>
  );
}
