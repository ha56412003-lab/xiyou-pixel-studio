package com.cn.common.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

/**
 * 本地文件存储实现（替代原阿里云 OSS 版本）。
 *
 * 文件写到本机目录（默认 F:/aistudio-site/data/uploads），
 * 由 FileServeController 以 /api/files/** 对外提供访问。
 *
 * 可用环境变量覆盖：
 *   LOCAL_UPLOAD_DIR  存储根目录
 *   FILE_PUBLIC_BASE  对外访问前缀（默认 http://127.0.0.1:9000/api）
 *
 * 方法签名与原 OSS 版本保持一致，调用方无需改动。
 * 上云时恢复 OSS 实现即可（git 历史里有原版）。
 *
 * @author 时间海 @github dulaiduwang003 (原版) / 罗小黑 (本地化改造)
 */
@Component
@SuppressWarnings("all")
public class UploadUtil {

    private static final String BASE_DIR    = System.getenv().getOrDefault("LOCAL_UPLOAD_DIR", "F:/aistudio-site/data/uploads");
    private static final String PUBLIC_BASE = System.getenv().getOrDefault("FILE_PUBLIC_BASE", "http://127.0.0.1:9000/api");

    public String uploadFile(final MultipartFile file, final String path) {
        try (InputStream inputStream = file.getInputStream()) {
            String originalFileName = file.getOriginalFilename();
            assert originalFileName != null;
            String fileName = UUID.randomUUID() + originalFileName.substring(originalFileName.lastIndexOf('.'));
            return saveStream(inputStream, path, fileName);
        } catch (Exception e) {
            throw new RuntimeException("本地保存上传文件失败", e);
        }
    }

    public String uploadUrl(String imageUrl, String path) {
        try {
            URL url = new URL(imageUrl);
            String fileNameWithExt = null;

            // 尝试从查询参数中获取文件名
            String query = url.getQuery();
            if (query != null && query.contains("filename=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("filename=")) {
                        fileNameWithExt = URLDecoder.decode(param.substring("filename=".length()), "UTF-8");
                        break;
                    }
                }
            }
            // 如果没有从查询参数中获取到文件名，则使用 URL 路径中的文件名
            if (fileNameWithExt == null) {
                String p = url.getPath();
                fileNameWithExt = p.substring(p.lastIndexOf('/') + 1);
            }

            String fileName = UUID.randomUUID().toString() + "." + getFileExtension(fileNameWithExt);
            try (InputStream in = url.openStream()) {
                return saveStream(in, path, fileName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload media from URL: " + imageUrl, e);
        }
    }

    public String uploadBase64Image(String base64OrDataUri, String path) {
        try {
            String base64Data = base64OrDataUri;
            if (base64OrDataUri != null && base64OrDataUri.startsWith("data:")) {
                int commaIdx = base64OrDataUri.indexOf(',');
                if (commaIdx > 0) {
                    base64Data = base64OrDataUri.substring(commaIdx + 1);
                }
            }
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            String fileName = UUID.randomUUID().toString() + ".png";
            return saveStream(new ByteArrayInputStream(decodedBytes), path, fileName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload base64 image (PNG only)", e);
        }
    }

    /**
     * 根据公开访问 URL 删除本地文件（删作品时同步清理磁盘，避免废片一直堆积）。
     * 例：https://xiyou.hahayuyue.cn/api/files/COMFYUI/xxx.png → {LOCAL_UPLOAD_DIR}/COMFYUI/xxx.png
     *
     * @return true=文件已删除；false=文件不存在或不是本站文件（不抛异常，删作品不受影响）
     */
    public boolean deleteByUrl(final String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String u = url;
            int filesIdx = u.indexOf("/files/");
            if (filesIdx < 0) {
                // 兼容只有相对路径的情况，如 COMFYUI/xxx.png
                if (u.contains("://")) {
                    return false;
                }
            } else {
                u = u.substring(filesIdx + "/files/".length());
            }
            int q = u.indexOf('?');
            if (q >= 0) {
                u = u.substring(0, q);
            }
            // URL 解码（中文文件名）
            u = URLDecoder.decode(u, java.nio.charset.StandardCharsets.UTF_8);

            Path root = Paths.get(BASE_DIR).normalize();
            Path target = root.resolve(u).normalize();
            if (!target.startsWith(root)) {
                return false;
            }
            return Files.deleteIfExists(target);
        } catch (Exception e) {
            return false;
        }
    }

    /** 写入本地目录并返回公开访问 URL */
    private String saveStream(InputStream in, String path, String fileName) throws Exception {
        Path root = Paths.get(BASE_DIR).normalize();
        Path dir = root.resolve(path).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("illegal path: " + path);
        }
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        // 顺带生成首页卡片用的缩略图（480px JPEG），省带宽
        try {
            makeThumb(target, fileName);
        } catch (Exception e) {
            // 缩略图失败不影响上传
        }
        return PUBLIC_BASE + "/files/" + path + "/" + fileName;
    }

    /**
     * 生成缩略图：THUMB/&lt;原文件名&gt;.jpg（长边 480、质量 0.82）。
     * 前端卡片用 /api/files/THUMB/xxx.jpg，拿不到时回退原图。
     */
    private void makeThumb(final Path source, final String fileName) throws Exception {
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            return; // webp 等 ImageIO 读不了，跳过（前端会回退原图）
        }
        java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(source.toFile());
        if (src == null) {
            return;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int max = 480;
        if (w <= max && h <= max) {
            return; // 已经很小，不用缩
        }
        double scale = (double) max / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(
                nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();

        Path thumbDir = Paths.get(BASE_DIR).normalize().resolve("THUMB");
        Files.createDirectories(thumbDir);
        Path out = thumbDir.resolve(fileName.substring(0, fileName.lastIndexOf('.')) + ".jpg");
        javax.imageio.ImageIO.write(dst, "jpg", out.toFile());
    }

    private String getMimeType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        String f = fileName.toLowerCase();
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".png"))  return "image/png";
        if (f.endsWith(".webp")) return "image/webp";
        if (f.endsWith(".gif"))  return "image/gif";
        if (f.endsWith(".mp4"))  return "video/mp4";
        if (f.endsWith(".avi"))  return "video/x-msvideo";
        if (f.endsWith(".mp3"))  return "audio/mpeg";
        if (f.endsWith(".wav"))  return "audio/wav";
        // 默认返回二进制流类型
        return "application/octet-stream";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return ""; // 没有扩展名
        }
        return fileName.substring(lastIndexOfDot + 1);
    }
}
