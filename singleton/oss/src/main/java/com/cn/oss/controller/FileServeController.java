package com.cn.oss.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件访问：把 UploadUtil 存到本地的文件以 /files/** 提供 HTTP 访问。
 * 替代原 OSS 域名直链。路径已做目录穿越防护。
 */
@RestController
@RequestMapping("/files")
public class FileServeController {

    private static final String BASE_DIR = System.getenv().getOrDefault("LOCAL_UPLOAD_DIR", "F:/aistudio-site/data/uploads");

    @GetMapping("/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request,
                                          @org.springframework.web.bind.annotation.RequestParam(value = "download", required = false) String download) {
        try {
            String full = request.getRequestURI();               // /api/files/<path>/<name>
            String rel = full.substring(full.indexOf("/files/") + "/files/".length());
            Path root = Paths.get(BASE_DIR).normalize();
            Path target = root.resolve(rel).normalize();
            if (!target.startsWith(root) || !Files.exists(target)) {
                return ResponseEntity.notFound().build();
            }
            Resource res = new FileSystemResource(target);
            String name = target.getFileName().toString().toLowerCase();
            MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
            if (name.endsWith(".png")) mt = MediaType.IMAGE_PNG;
            else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mt = MediaType.IMAGE_JPEG;
            else if (name.endsWith(".webp")) mt = MediaType.parseMediaType("image/webp");
            else if (name.endsWith(".svg")) mt = MediaType.parseMediaType("image/svg+xml");
            else if (name.endsWith(".gif")) mt = MediaType.IMAGE_GIF;
            else if (name.endsWith(".mp4")) mt = MediaType.parseMediaType("video/mp4");
            else if (name.endsWith(".mp3")) mt = MediaType.parseMediaType("audio/mpeg");
            else if (name.endsWith(".wav")) mt = MediaType.parseMediaType("audio/wav");
            if ("1".equals(download) || "true".equalsIgnoreCase(download)) {
                String fn = target.getFileName().toString();
                return ResponseEntity.ok()
                        .contentType(mt)
                        .header("Content-Disposition", "attachment; filename=\"" + fn + "\"; filename*=UTF-8''" + java.net.URLEncoder.encode(fn, "UTF-8"))
                        .body(res);
            }
            return ResponseEntity.ok().contentType(mt).body(res);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
