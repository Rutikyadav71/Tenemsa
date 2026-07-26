package com.tenemsa.platform.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "sender_id",    nullable = false) private Integer senderId;
    @Column(name = "sender_role",  length = 10)      private String senderRole;
    @Column(name = "receiver_id",  nullable = false) private Integer receiverId;
    @Column(name = "receiver_role",length = 10)      private String receiverRole;
    @Column(columnDefinition = "TEXT", nullable = false) private String message;
    @Column(nullable = false) private LocalDateTime timestamp;
    @Column(length = 20)      private String status;
    @Column(name = "reaction", length = 10) private String reaction;
    @Column(name = "starred")               private Boolean starred = false;

    public ChatMessage(){}
    public ChatMessage(Integer sid,String sr,Integer rid,String rr,String msg){
        senderId=sid; senderRole=sr; receiverId=rid; receiverRole=rr; message=msg;
        timestamp=LocalDateTime.now(); status="SENT";
    }
    public Long    getId()             {return id;} public void setId(Long id)                  {this.id=id;}
    public Integer getSenderId()       {return senderId;} public void setSenderId(Integer s)    {senderId=s;}
    public String  getSenderRole()     {return senderRole;} public void setSenderRole(String s) {senderRole=s;}
    public Integer getReceiverId()     {return receiverId;} public void setReceiverId(Integer r){receiverId=r;}
    public String  getReceiverRole()   {return receiverRole;} public void setReceiverRole(String r){receiverRole=r;}
    public String  getMessage()        {return message;} public void setMessage(String m)       {message=m;}
    public LocalDateTime getTimestamp(){return timestamp;} public void setTimestamp(LocalDateTime t){timestamp=t;}
    public String  getStatus()         {return status;} public void setStatus(String s)         {status=s;}
    public String  getReaction()       {return reaction;} public void setReaction(String r)     {reaction=r;}
    public Boolean getStarred()        {return starred!=null&&starred;} public void setStarred(Boolean s){starred=s!=null&&s;}
}