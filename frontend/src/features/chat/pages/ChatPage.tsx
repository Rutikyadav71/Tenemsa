import { useEffect, useRef, useState, type TouchEvent } from "react";
import { connect, disconnect } from "../services/chatService";
import ChatSidebar from "../components/ChatSidebar";
import ChatRoom from "../components/ChatRoom";
import type { ChatMessage, ChatRoleTag, ChatUser } from "../../../shared/types/models";
import "../chat.css";

const MenuIcon = () => <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M3 12h18M3 6h18M3 18h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const ChevL    = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const ChevR    = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M9 18l6-6-6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const BackIcon = () => <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M19 12H5M12 19l-7-7 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const ChatIco  = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;

export default function ChatPage() {
  const role          = localStorage.getItem("role") as ChatRoleTag | null;
  const rawId         = role==="ADMIN" ? localStorage.getItem("id") : localStorage.getItem("empId");
  const currentUserId = Number(rawId);

  const [selectedUser,    setSelectedUser]    = useState<ChatUser | null>(null);
  const [refreshTrigger,  setRefreshTrigger]  = useState(0);
  const [incomingMsg,     setIncomingMsg]     = useState<ChatMessage | null>(null);
  const [sidebarCollapsed,setSidebarCollapsed]= useState(false); // desktop
  const [mobileSbOpen,    setMobileSbOpen]    = useState(true);  // mobile

  const isMob = () => window.innerWidth <= 768;
  const touchX0 = useRef(0), touchX1 = useRef(0);

  useEffect(() => {
    if (!currentUserId||isNaN(currentUserId)||!role) return;
    connect(currentUserId, role, (msg: ChatMessage) => {
      setIncomingMsg(msg);
      setRefreshTrigger(p=>p+1);
    });
    return () => disconnect();
  }, [currentUserId, role]);

  const handleSelectUser = (user: ChatUser) => {
    setSelectedUser(user);
    setIncomingMsg(null);
    setRefreshTrigger(p=>p+1);
    if (isMob()) setMobileSbOpen(false);
  };

  const toggleSidebar = () => {
    if (isMob()) setMobileSbOpen(p=>!p);
    else setSidebarCollapsed(p=>!p);
  };

  const handleBack = () => { setMobileSbOpen(true); setSelectedUser(null); };

  const onTouchStart = (e: TouchEvent) => { touchX0.current=e.touches[0].clientX; };
  const onTouchEnd   = (e: TouchEvent) => {
    touchX1.current=e.changedTouches[0].clientX;
    const d=touchX1.current-touchX0.current;
    if (isMob()) {
      if (d>60)  setMobileSbOpen(true);
      if (d<-60) setMobileSbOpen(false);
    }
  };

  // Sidebar CSS class
  const sbClass = isMob()
    ? `chat-sidebar-wrapper ${mobileSbOpen?"open":"closed"}`
    : `chat-sidebar-wrapper ${sidebarCollapsed?"closed":"open"}`;

  // Chat room hidden on mobile when sidebar is open and no user selected
  const chatHidden = isMob()&&mobileSbOpen&&!selectedUser;

  return (
    <div className="chat-page-wrapper" onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}>
      {/* Top bar */}
      <div className="chat-topbar">
        {isMob()&&selectedUser&&!mobileSbOpen
          ? <button className="chat-toggle-btn" onClick={handleBack} title="Back"><BackIcon/></button>
          : <button className="chat-toggle-btn" onClick={toggleSidebar}
              title={isMob()?"Contacts":(sidebarCollapsed?"Show contacts":"Hide contacts")}>
              {isMob()?<MenuIcon/>:(sidebarCollapsed?<ChevR/>:<ChevL/>)}
            </button>
        }
        <span className="chat-title">
          <ChatIco/>
          {selectedUser?`Chatting with ${selectedUser.name}`:"Chat Room"}
        </span>
      </div>

      {/* Body */}
      <div className="chat-container">
        {/* Mobile overlay backdrop */}
        {isMob()&&mobileSbOpen&&selectedUser&&(
          <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.5)",zIndex:19}}
            onClick={()=>setMobileSbOpen(false)}/>
        )}

        {/* Sidebar */}
        <div className={sbClass}>
          <ChatSidebar
            currentUserId={currentUserId}
            currentUserRole={role}
            onSelectUser={handleSelectUser}
            selectedUser={selectedUser}
            refreshTrigger={refreshTrigger}
          />
        </div>

        {/* Chat room */}
        {!chatHidden && (
          <div className="chat-room-wrapper">
            <ChatRoom
              key={`${selectedUser?.id||"none"}_${selectedUser?.role||""}`}
              currentUserId={currentUserId}
              currentUserRole={role}
              selectedUser={selectedUser}
              incomingMessage={incomingMsg}
              onMessageSent={()=>setRefreshTrigger(p=>p+1)}
            />
          </div>
        )}
      </div>
    </div>
  );
}
