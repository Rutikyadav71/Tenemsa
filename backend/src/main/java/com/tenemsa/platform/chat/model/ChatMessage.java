package com.tenemsa.platform.chat.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_company", columnList = "company_id"))
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "sender_id",    nullable = false) private Integer senderId;
    @Column(name = "sender_role",  length = 10)      private String senderRole;
    @Column(name = "receiver_id",  nullable = false) private Integer receiverId;
    @Column(name = "receiver_role",length = 10)      private String receiverRole;
    @Column(columnDefinition = "TEXT", nullable = false) private String message;
    @Column(nullable = false) private LocalDateTime timestamp;
    @Column(length = 20)      private String status;
    @Column(name = "reaction", length = 10) private String reaction;
    @Column(name = "starred")               private Boolean starred = false;
    // Phase 8d: attachment metadata is copied from the stored attachment by the server, never taken from the client.
    @Column(name = "attachment_id")                   private Long attachmentId;
    @Column(name = "attachment_name", length = 255)   private String attachmentName;
    @Column(name = "attachment_type", length = 120)   private String attachmentType;
    @Column(name = "attachment_size")                 private Long attachmentSize;

    public ChatMessage(){}
    public ChatMessage(Integer sid,String sr,Integer rid,String rr,String msg){
        senderId=sid; senderRole=sr; receiverId=rid; receiverRole=rr; message=msg;
        timestamp=LocalDateTime.now(); status="SENT";
    }
    public Long    getId()             {return id;} public void setId(Long id)                  {this.id=id;}
    public Long getCompanyId(){return companyId;} public void setCompanyId(Long c){companyId=c;}
    public Integer getSenderId()       {return senderId;} public void setSenderId(Integer s)    {senderId=s;}
    public String  getSenderRole()     {return senderRole;} public void setSenderRole(String s) {senderRole=s;}
    public Integer getReceiverId()     {return receiverId;} public void setReceiverId(Integer r){receiverId=r;}
    public String  getReceiverRole()   {return receiverRole;} public void setReceiverRole(String r){receiverRole=r;}
    public String  getMessage()        {return message;} public void setMessage(String m)       {message=m;}
    public LocalDateTime getTimestamp(){return timestamp;} public void setTimestamp(LocalDateTime t){timestamp=t;}
    public String  getStatus()         {return status;} public void setStatus(String s)         {status=s;}
    public String  getReaction()       {return reaction;} public void setReaction(String r)     {reaction=r;}
    public Boolean getStarred()        {return starred!=null&&starred;} public void setStarred(Boolean s){starred=s!=null&&s;}
    public Long   getAttachmentId()    {return attachmentId;}   public void setAttachmentId(Long a)    {attachmentId=a;}
    public String getAttachmentName()  {return attachmentName;} public void setAttachmentName(String a){attachmentName=a;}
    public String getAttachmentType()  {return attachmentType;} public void setAttachmentType(String a){attachmentType=a;}
    public Long   getAttachmentSize()  {return attachmentSize;} public void setAttachmentSize(Long a)  {attachmentSize=a;}
}
