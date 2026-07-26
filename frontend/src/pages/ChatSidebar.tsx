import { useEffect, useMemo, useState, type SyntheticEvent } from "react";
import axiosInstance from "../services/axiosInstance";
import type { ChatMessage, ChatRoleTag, ChatUser } from "../types/models";
import "./chat.css";

const AV = ["#6366f1","#2dd4bf","#22c55e","#f59e0b","#ef4444","#a78bfa","#38bdf8","#fb923c"];
const getColor    = (n?: string | null) => AV[(n?.charCodeAt(0)||0)%AV.length];
const getInitials = (n?: string | null) => n?.split(" ").map(x=>x[0]).join("").toUpperCase().slice(0,2)||"?";

/* Correctly renders a circle avatar — image or colored initials */
function SidebarAvatar({ user, size=42 }: { user?: ChatUser | null; size?: number }) {
  const c = getColor(user?.name||"");
  return (
    <div style={{width:size,height:size,borderRadius:"50%",flexShrink:0,
      background:`${c}22`,color:c,overflow:"hidden",display:"flex",
      alignItems:"center",justifyContent:"center",
      fontSize:size*0.35,fontWeight:700,fontFamily:"'Outfit',sans-serif",
      position:"relative"}}>
      {user?.profileImageUrl
        ? <img src={user.profileImageUrl} alt={user?.name}
            style={{width:"100%",height:"100%",objectFit:"cover",display:"block"}}
            onError={(e: SyntheticEvent<HTMLImageElement>)=>{(e.target as HTMLImageElement).style.display="none";}}/>
        : getInitials(user?.name)
      }
    </div>
  );
}

interface ChatSidebarProps {
  currentUserId: number;
  currentUserRole: ChatRoleTag | null;
  onSelectUser: (user: ChatUser) => void;
  selectedUser: ChatUser | null;
  refreshTrigger: number;
}

export default function ChatSidebar({ currentUserId, currentUserRole, onSelectUser, selectedUser, refreshTrigger }: ChatSidebarProps) {
  const [allUsers, setAllUsers]   = useState<{employees: ChatUser[]; admins: ChatUser[]}>({employees:[],admins:[]});
  const [lastMap,  setLastMap]    = useState<Record<string, string>>({});
  const [unreadMap,setUnreadMap]  = useState<Record<string, number>>({});
  const [search,   setSearch]     = useState("");

  useEffect(() => {
    axiosInstance.get<{employees: ChatUser[]; admins: ChatUser[]}>("/api/chat-users").then(res=>{
      setAllUsers({employees:res.data.employees||[],admins:res.data.admins||[]});
    }).catch(()=>{});
  }, [currentUserId]);

  const allFlat = useMemo(() => {
    const emp = (allUsers.employees||[]).map(u=>({...u,id:Number(u.id)}));
    const adm = (allUsers.admins||[]).map(u=>({...u,id:Number(u.id)}));
    return [...adm,...emp].filter(u=>!(Number(u.id)===Number(currentUserId)&&u.role===currentUserRole));
  },[allUsers,currentUserId,currentUserRole]);

  useEffect(() => {
    if (!allFlat.length) return;
    const fetchPreviews = async () => {
      const um: Record<string, number> = {}, lm: Record<string, string> = {};
      await Promise.all(allFlat.map(async u=>{
        const key=`${u.id}_${u.role}`;
        try {
          const res=await axiosInstance.get<ChatMessage[]>("/api/chat/history",{
            params:{id1:currentUserId,role1:currentUserRole,id2:Number(u.id),role2:u.role}
          });
          const msgs=res.data||[];
          um[key]=msgs.filter(m=>Number(m.senderId)===Number(u.id)&&m.senderRole===u.role&&m.status!=="READ").length;
          const last=msgs.filter(m=>m.status!=="DELETED"&&m.message!=="[deleted]").slice(-1)[0];
          if (last) {
            let p=last.message||"";
            if (p.startsWith("[FILE:"))     p="📎 File attachment";
            else if (p.startsWith("[REPLY:")) p=p.replace(/^\[REPLY:.+?:.+?\]/s,"");
            lm[key]=p.slice(0,45)+(p.length>45?"…":"");
          }
        } catch {}
      }));
      setUnreadMap(um); setLastMap(lm);
    };
    fetchPreviews();
  },[allFlat,refreshTrigger]);

  useEffect(() => {
    if (!selectedUser) return;
    const key=`${selectedUser.id}_${selectedUser.role}`;
    setUnreadMap(p=>({...p,[key]:0}));
  },[selectedUser]);

  const filtered = useMemo(() => {
    const q=search.toLowerCase();
    return allFlat.filter(u=>u.name?.toLowerCase().includes(q)||u.department?.toLowerCase().includes(q));
  },[allFlat,search]);

  const admins = filtered.filter(u=>u.role==="ADMIN");
  const emps   = filtered.filter(u=>u.role!=="ADMIN");
  const isSel  = (u: ChatUser) => selectedUser!=null && Number(selectedUser.id)===Number(u.id)&&selectedUser.role===u.role;

  const UserItem = ({ user }: { user: ChatUser }) => {
    const key    = `${user.id}_${user.role}`;
    const unread = unreadMap[key]||0;
    const last   = lastMap[key]||"";
    const isAdmin= user.role==="ADMIN";
    const c      = getColor(user.name||"");
    return (
      <div className={`chat-user-item${isSel(user)?" selected":""}`} onClick={()=>onSelectUser(user)}>
        <div style={{position:"relative",flexShrink:0}}>
          <SidebarAvatar user={user} size={42}/>
          {isAdmin&&(
            <div style={{position:"absolute",bottom:0,right:0,width:12,height:12,borderRadius:"50%",
              background:"var(--accent)",border:"2px solid var(--bg-surface)",
              display:"flex",alignItems:"center",justifyContent:"center"}}>
              <svg width="6" height="6" viewBox="0 0 24 24" fill="white">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
            </div>
          )}
        </div>
        <div style={{flex:1,minWidth:0}}>
          <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",gap:4}}>
            <div className="chat-user-name" style={{color:isSel(user)?"var(--accent-light)":"var(--text-1)"}}>{user.name}</div>
            {unread>0&&<div className="chat-unread-badge">{unread>99?"99+":unread}</div>}
          </div>
          <div className="chat-user-role" style={{color:isAdmin?"var(--accent-light)":"var(--text-3)"}}>
            {isAdmin?"Admin":(user.department||`Emp #${user.id}`)}
          </div>
          {last&&<div className="chat-user-last">{last}</div>}
        </div>
      </div>
    );
  };

  return (
    <div className="chat-user-list">
      <div className="chat-search-wrap">
        <div className="search-bar">
          <svg width="13" height="13" fill="none" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="1.8"/>
            <path d="M21 21l-4.35-4.35" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <input placeholder="Search people..." value={search} onChange={e=>setSearch(e.target.value)}/>
        </div>
      </div>

      {filtered.length===0
        ? <div style={{padding:"24px",textAlign:"center",fontSize:13,color:"var(--text-3)"}}>No results</div>
        : <>
            {admins.length>0&&<>
              <div className="chat-section-label">Administrators</div>
              {admins.map(u=><UserItem key={`${u.id}_${u.role}`} user={u}/>)}
            </>}
            {emps.length>0&&<>
              <div className="chat-section-label" style={{borderTop:admins.length>0?"1px solid var(--border)":"none",marginTop:admins.length>0?4:0}}>
                Employees
              </div>
              {emps.map(u=><UserItem key={`${u.id}_${u.role}`} user={u}/>)}
            </>}
          </>
      }
    </div>
  );
}
