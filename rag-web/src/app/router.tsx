import { createBrowserRouter, Navigate } from "react-router-dom";
import { DashboardLayout } from "./layouts/DashboardLayout";
import { LoginPage } from "../modules/auth/pages/LoginPage";
import { ChatPage } from "../modules/chat/pages/ChatPage";
import { KnowledgePage } from "../modules/knowledge/pages/KnowledgePage";
import { KnowledgeDocumentsPage } from "../modules/knowledge/pages/KnowledgeDocumentsPage";
import { IntentTreePage } from "../modules/intent/pages/IntentTreePage";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />
  },
  {
    path: "/chat",
    element: <ChatPage />
  },
  {
    path: "/",
    element: <DashboardLayout />,
    children: [
      { index: true, element: <Navigate to="/chat" replace /> },
      { path: "knowledge", element: <KnowledgePage /> },
      { path: "knowledge/:kbId/docs", element: <KnowledgeDocumentsPage /> },
      { path: "intent-tree", element: <IntentTreePage /> }
    ]
  }
]);
