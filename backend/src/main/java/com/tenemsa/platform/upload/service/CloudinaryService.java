package com.tenemsa.platform.upload.service;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@Service
public class CloudinaryService {
    @Autowired private Cloudinary cloudinary;
    private static final List<String> ALLOWED_IMG = Arrays.asList("image/jpeg","image/jpg","image/png","image/webp");
    private static final long MAX_PROFILE = 2L*1024*1024;
    private static final long MAX_CHAT    = 10L*1024*1024;

    @SuppressWarnings("unchecked")
    public String uploadProfileImage(MultipartFile file, String ref) throws IOException {
        String ct = file.getContentType();
        if (ct==null||!ALLOWED_IMG.contains(ct)) throw new IllegalArgumentException("Only JPG/PNG/WEBP allowed.");
        if (file.getSize()>MAX_PROFILE) throw new IllegalArgumentException("Max 2 MB for profile image.");
        Map<?,?> r = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
            "public_id","ems/profiles/"+ref,"overwrite",true,"resource_type","image",
            "transformation","f_auto,q_auto,w_400,h_400,c_fill,g_face"));
        return (String) r.get("secure_url");
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> uploadChatFile(MultipartFile file, String senderRef) throws IOException {
        if (file.getSize()>MAX_CHAT) throw new IllegalArgumentException("Max 10 MB for chat files.");

        String origName = file.getOriginalFilename()!=null ? file.getOriginalFilename() : "file";
        boolean isImg   = file.getContentType()!=null && file.getContentType().startsWith("image/");

        Map<String,Object> result = new HashMap<>();
        if (isImg) {
            Map<?,?> r = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "public_id","ems/chat/img_"+senderRef+"_"+System.currentTimeMillis(),
                "resource_type","image","overwrite",false,"use_filename",true,"unique_filename",true));
            result.put("url", r.get("secure_url"));
            result.put("isImage", true);
        } else {
            // Include extension in public_id so Cloudinary URL ends with correct extension
            String ext      = origName.contains(".")? origName.substring(origName.lastIndexOf('.')):"";
            String base     = origName.contains(".")? origName.substring(0,origName.lastIndexOf('.')):"origName";
            String safeBase = base.replaceAll("[^a-zA-Z0-9_-]","_").substring(0,Math.min(base.length(),40));
            String pid      = "ems/chat/"+senderRef+"_"+System.currentTimeMillis()+"_"+safeBase+ext;

            Map<?,?> r = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "public_id",pid,"resource_type","raw","overwrite",false,
                "use_filename",false,"unique_filename",false));

            String secureUrl = (String) r.get("secure_url");
            result.put("url", secureUrl);
            result.put("isImage", false);
        }
        result.put("fileName", origName);
        result.put("fileSize", file.getSize());
        result.put("mimeType", file.getContentType()!=null?file.getContentType():"application/octet-stream");
        return result;
    }
}