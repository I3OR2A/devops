package com.ioariroi.file.controller;

import com.ioariroi.commonutils.R;
import com.ioariroi.file.entity.SmbEntityQuery;
import com.ioariroi.file.entity.SmbEntity;
import com.ioariroi.file.utils.FileUtils;
import com.ioariroi.file.utils.ZipUtils;
import jcifs.CIFSException;
import jcifs.CloseableIterator;
import jcifs.smb.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@CrossOrigin //跨域
@RestController
@RequestMapping("/file")
public class FilesController {

    @Value("${ntlm.username}")
    private String username;

    @Value("${ntlm.password}")
    private String password;

//    @GetMapping(value = "/list")
//    public void listFiles() {
//        try {
//            Properties prop = new Properties();
//            prop.put("jcifs.smb.client.enableSMB2", "true");
//            prop.put("jcifs.smb.client.disableSMB1", "false");
//            prop.put("jcifs.traceResources", "true");
//            Configuration config = new PropertyConfiguration(prop);
//            CIFSContext baseContext = new BaseContext(config);
//            CIFSContext contextWithCred = baseContext.withCredentials(new NtlmPasswordAuthenticator(null, "CCWUAE", "nino0506"));
//
//            SmbFile smbFile = new SmbFile("smb://localhost", contextWithCred);
//
//            if (smbFile.isDirectory()) {
//                List<SmbFile> files = Arrays.asList(smbFile.listFiles());
//                for (SmbFile file : files) {
//                    if (file.isDirectory()) {
//                        System.out.println("Directory: " + file.getName());
//                    }
//                    if (file.isFile()) {
//                        System.out.println("File: " + file.getName());
//                    }
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    @GetMapping(value = "/listRemoteFiles")
    public R listRemoteFiles(@RequestParam String hostname, @RequestParam String account, @RequestParam String path) {
        SmbFile smbFile = null;
        List<SmbEntity> smbEntities = new ArrayList<>();
        try {
            String requestPath, basePath;
            basePath = requestPath = "smb://" + hostname + "/";
            requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path + "/" : requestPath;
            System.out.println(requestPath);
            smbFile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
            // 獲取資料夾下的所有子檔案
            CloseableIterator ci = smbFile.children();
            // SmbFile[] smbFiles = smbFile.listFiles();

            while (ci.hasNext()) {
                SmbFile tmpFile = (SmbFile) ci.next();
                if (tmpFile.isHidden()) continue;

                SmbEntity smbEntity = new SmbEntity();
                // 去除最後一個反斜線取得檔案名稱
                if (tmpFile.getName().endsWith("/")) {
                    smbEntity.setName(tmpFile.getName().substring(0, tmpFile.getName().length() - 1));
                } else {
                    smbEntity.setName(tmpFile.getName());
                }
                smbEntity.setSize(tmpFile.length());
                smbEntity.setParent(tmpFile.getParent());

                smbEntity.setPath(tmpFile.getParent().substring(basePath.length()) + smbEntity.getName());

                smbEntity.setCreateTime(tmpFile.createTime());
                smbEntity.setLastAccess(tmpFile.lastAccess());
                smbEntity.setLastModified(tmpFile.lastModified());
                smbEntity.setDirectory(tmpFile.isDirectory());
                smbEntity.setHidden(tmpFile.isHidden());
                smbEntities.add(smbEntity);
            }

            return R.ok().data("items", smbEntities).data("total", smbEntities.size());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (CIFSException e) {
            e.printStackTrace();
        }

        return R.error();
    }

    @GetMapping(value = "/createRemoteFile")
    public R createRemoteFile(@RequestParam String hostname, @RequestParam String account, @RequestParam String path) {
        String requestPath = "smb://" + hostname + "/";
        requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path : requestPath;
        System.out.println("createRemoteFile: " + requestPath);

        try {
            SmbFile rmifile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
            if (!rmifile.exists() || (rmifile.exists() && rmifile.isDirectory()))
                rmifile.createNewFile();
        } catch (MalformedURLException | CIFSException e) {
            e.printStackTrace();
            return R.error().message("檔案新增失敗");
        }
        return R.ok();
    }

    @GetMapping(value = "/createRemoteDir")
    public R createRemoteDir(@RequestParam String hostname, @RequestParam String account, @RequestParam String path) {
        String requestPath = "smb://" + hostname + "/";
        requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path + "/" : requestPath;
        System.out.println("createRemoteDir: " + requestPath);

        try {
            SmbFile rmifile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
            if (!rmifile.exists() || (rmifile.exists() && rmifile.isFile()))
                rmifile.mkdirs();
        } catch (MalformedURLException | CIFSException e) {
            e.printStackTrace();
            return R.error().message("目錄新增失敗");
        }
        return R.ok();
    }

    @GetMapping(value = "/deleteRemoteFile")
    public R deleteRemoteFile(@RequestParam String hostname, @RequestParam String account, @RequestParam String path) {
        String requestPath = "smb://" + hostname + "/";
        requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path : requestPath;

        try {
            SmbFile rmifile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
            boolean result = FileUtils.removeFile(rmifile);

            if (result) {
                return R.ok();
            } else {
                return R.error().message("檔案刪除失敗");
            }
        } catch (MalformedURLException | CIFSException e) {
            e.printStackTrace();
            return R.error().message("檔案刪除失敗");
        }
    }

    @GetMapping(value = "/deleteRemoteDir")
    public R deleteRemoteDir(@RequestParam String hostname, @RequestParam String account, @RequestParam String path) {
        String requestPath = "smb://" + hostname + "/";
        requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path + "/" : requestPath;
        try {
            SmbFile rmifile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
            FileUtils.removeDir(rmifile);
        } catch (CIFSException | MalformedURLException e) {
            e.printStackTrace();
            return R.error().message("目錄刪除失敗");
        }
        return R.ok();
    }

    @PostMapping(value = "/uploadRemoteFile")
    public R uploadRemoteFile(@RequestParam String hostname, @RequestParam String account, @RequestParam String path, @RequestParam("file") MultipartFile file) {
        String requestPath = "smb://" + hostname + "/";
        requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path + "/" : requestPath;
        requestPath = requestPath + file.getOriginalFilename();
        System.out.println(requestPath);
        try {
            SmbFile smbFile = FileUtils.saveSMB(file, requestPath, FileUtils.getAuth(null, username, password));
        } catch (CIFSException e) {
            e.printStackTrace();
            return R.error();
        }
        return R.ok();
    }

    @PostMapping(value = "/uploadRemoteFileAndUnzip")
    public void uploadRemoteFileAndUnzip(@RequestParam String hostname, @RequestParam String account, @RequestParam String path, @RequestParam("file") MultipartFile file) throws IOException {
        String requestPath = "smb://" + hostname + "/";
        requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path + "/" : requestPath;
        requestPath = requestPath + file.getOriginalFilename();
        System.out.println(requestPath);
        // 上傳文件至 Server 端
        SmbFile smbFile = FileUtils.saveSMB(file, requestPath, FileUtils.getAuth(null, username, password));

        // 解壓縮遠程文件
        ZipUtils.unzipSmbZip(smbFile.getPath(), smbFile.getParent(), FileUtils.getAuth(null, username, password));

        // 刪除遠程壓縮檔
        boolean result = FileUtils.removeFile(smbFile);
    }

    @GetMapping(value = "/downloadRemoteFile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadRemoteFile(@RequestParam String hostname, @RequestParam String account, @RequestParam String path) {
        String fileName = "";

        try {
            fileName = FilenameUtils.getName(path);

            StreamingResponseBody body = new StreamingResponseBody() {
                @Override
                public void writeTo(OutputStream outputStream) throws IOException {
                    int len;
                    long startTime = System.currentTimeMillis();
                    String requestPath = "smb://" + hostname + "/";
                    requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path + "/" : requestPath;
                    System.out.println(requestPath);

                    SmbFile rmifile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
                    InputStream fis = rmifile.getInputStream();

                    byte[] content = new byte[1024];
                    while ((len = fis.read(content, 0, content.length)) != -1) {
                        outputStream.write(content, 0, len);
                    }
                    long userTime = System.currentTimeMillis() - startTime;

                    System.out.println("使用时间" + userTime);
                }
            };

//            //处理文件名有中文问题
//            if (request.getHeader("User-Agent").toUpperCase().indexOf("MSIE") > 0) {
//                fileName= URLEncoder.encode(fileName,"UTF-8");
//            } else {
//                fileName= new String(fileName.getBytes(),"ISO-8859-1");
//            }
//
//            return ResponseEntity.ok()
//                    .header("Content-Disposition","attachment;filename=\""+fileName+"\"") // 内容描述
//                    .header("requestType","file") // 自定义的header
//                    .header("Access-Control-Expose-Headers", "requestType") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
//                    .header("Access-Control-Expose-Headers", "Content-Disposition") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
//                    .body(body);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20"))
                    //";filename*=UTF-8" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20")) // 内容描述
                    .header("requestType", "file") // 自定义的header
                    .header("Access-Control-Expose-Headers", "requestType") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
                    .header("Access-Control-Expose-Headers", "Content-Disposition") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
                    .body(body);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/downloadZipRemoteMultiFile")
    public ResponseEntity<StreamingResponseBody> downloadZipRemoteMultiFile(@RequestBody SmbEntityQuery smbEntityQuery) {


        String fileName = "";

        try {
            String requestPath = "smb://" + smbEntityQuery.getHostname() + "/";
            SmbFile rmifile = null;
            String targetPath = null;
//            if (!isDirectory) {
//                fileName = FilenameUtils.getName(path);
//
//            } else {
//                // 取得最後一個斜線前的資料夾名稱
//                if (StringUtils.isNoneEmpty(path) && path.charAt(path.length() - 1) == '/') {
//                    fileName = path.substring(0, path.length() - 1);
//                }
//                fileName = FilenameUtils.getName(fileName);
//
//            }
            // 獲取當前要壓縮檔案和目錄SmbFile實例
            List<SmbFile> smbFiles = new ArrayList<SmbFile>();
            for (SmbEntity smbEntity : smbEntityQuery.getSmbEntities()) {
                System.out.println("Name: " + smbEntity.getName());
                String tmpPath = smbEntity.isDirectory() ? requestPath + smbEntity.getPath() + "/" : requestPath + smbEntity.getPath();
                SmbFile smbFile = new SmbFile(tmpPath, FileUtils.getAuth(null, username, password));
                smbFiles.add(smbFile);
            }

            fileName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
            requestPath = StringUtils.isNoneEmpty(smbEntityQuery.getPath()) ? requestPath + smbEntityQuery.getPath() + "/" : requestPath;
            System.out.println("requestPath: " + requestPath);
            rmifile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
            targetPath = rmifile.getParent() + fileName;
            System.out.println("targetPath: " + targetPath);

            List<String> sourcePaths = new ArrayList<String>();
            for (SmbFile smbFile : smbFiles) {
                System.out.println("Current: " + smbFile.getPath());
                sourcePaths.add(smbFile.getPath());
            }
            SmbFile resultSmbFile = ZipUtils.createSmbZipFromMultiSrc(sourcePaths, targetPath, FileUtils.getAuth(null, username, password));

            StreamingResponseBody body = new StreamingResponseBody() {
                @Override
                public void writeTo(OutputStream outputStream) throws IOException {
                    int len;
                    long startTime = System.currentTimeMillis();
                    InputStream fis = resultSmbFile.getInputStream();

                    byte[] content = new byte[1024];
                    while ((len = fis.read(content, 0, content.length)) != -1) {
                        outputStream.write(content, 0, len);
                    }
                    outputStream.flush();
                    fis.close();
                    long userTime = System.currentTimeMillis() - startTime;

                    System.out.println("使用时间" + userTime);

                    // 刪除遠程壓縮檔
                    boolean result = FileUtils.removeFile(resultSmbFile);
                }
            };
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20"))
                    //";filename*=UTF-8" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20")) // 内容描述
                    .header("requestType", "file") // 自定义的header
                    .header("Access-Control-Expose-Headers", "requestType") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
                    .header("Access-Control-Expose-Headers", "Content-Disposition") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
                    .body(body);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/downloadZipRemoteFile")
//    public ResponseEntity<StreamingResponseBody> downloadZipRemoteFile(@RequestParam String hostname, @RequestParam String account, @RequestParam String path, @RequestParam(value = "isDirectory", required = false, defaultValue = "false") Boolean isDirectory) {
    public ResponseEntity<StreamingResponseBody> downloadZipRemoteFile(@RequestParam String hostname, @RequestParam String account, @RequestParam String path) {
        String fileName = "";

        try {
            String requestPath = "smb://" + hostname + "/";
            SmbFile rmifile = null;
            String targetPath = null;
//            if (!isDirectory) {
//                fileName = FilenameUtils.getName(path);
//
//            } else {
//                // 取得最後一個斜線前的資料夾名稱
//                if (StringUtils.isNoneEmpty(path) && path.charAt(path.length() - 1) == '/') {
//                    fileName = path.substring(0, path.length() - 1);
//                }
//                fileName = FilenameUtils.getName(fileName);
//
//            }
            fileName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
            requestPath = StringUtils.isNoneEmpty(path) ? requestPath + path + "/" : requestPath;
            System.out.println("requestPath: " + requestPath);
            rmifile = new SmbFile(requestPath, FileUtils.getAuth(null, username, password));
            targetPath = rmifile.getParent() + fileName;
            System.out.println("targetPath: " + targetPath);
            SmbFile smbFile = ZipUtils.createSmbZip(rmifile.getPath(), targetPath, FileUtils.getAuth(null, username, password));

            StreamingResponseBody body = new StreamingResponseBody() {
                @Override
                public void writeTo(OutputStream outputStream) throws IOException {
                    int len;
                    long startTime = System.currentTimeMillis();
                    InputStream fis = smbFile.getInputStream();

                    byte[] content = new byte[1024];
                    while ((len = fis.read(content, 0, content.length)) != -1) {
                        outputStream.write(content, 0, len);
                    }
                    outputStream.flush();
                    fis.close();
                    long userTime = System.currentTimeMillis() - startTime;

                    System.out.println("使用时间" + userTime);

                    // 刪除遠程壓縮檔
                    boolean result = FileUtils.removeFile(smbFile);
                }
            };
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20"))
                    //";filename*=UTF-8" + URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20")) // 内容描述
                    .header("requestType", "file") // 自定义的header
                    .header("Access-Control-Expose-Headers", "requestType") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
                    .header("Access-Control-Expose-Headers", "Content-Disposition") // 列表哪些header可以作为响应的一部分暴露给外部（除了默认的七种，其他的是不暴露给外部的）
                    .body(body);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/upload")
    public void uploadFile(@RequestParam("files") MultipartFile[] files) throws IOException {
        // 1. 用数组MultipartFile[]来表示多文件,所以遍历数组,对其中的文件进行逐一操作
        for (MultipartFile file : files) {
            // 2. 通过一顿file.getXXX()的操作,获取文件信息。
            // 2.1 这里用文件名举个例子
            String filename = file.getOriginalFilename();
            // 3. 接下来调用方法来保存文件到本地磁盘,返回的是保存后的文件路径
            String filePath = saveFileByNio((FileInputStream) file.getInputStream(), filename);
            // 4. 保存文件信息到数据库
            // 4.1 搞个实体类，把你需要的文件信息保存到实体类中
            // 4.2 调用Service层或者Dao层，保存数据库即可。
        }
    }

    public static String saveFileByNio(FileInputStream fis, String fileName) {
        // 这个路径最后是在: 你的项目路径/FileSpace  也就是和src同级
        String fileSpace = System.getProperty("user.dir") + File.separator + "FileSpace";
        String path = fileSpace + File.separator + fileName;
        // 判断父文件夹是否存在
        File file = new File(path);
        if (file.getParentFile() != null || !file.getParentFile().isDirectory()) {
            file.getParentFile().mkdirs();
        }
        // 通过NIO保存文件到本地磁盘
        try {
            FileOutputStream fos = new FileOutputStream(path);
            FileChannel inChannel = fis.getChannel();
            FileChannel outChannel = fos.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inChannel.close();
            outChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    @GetMapping(value = "/download/fileId/{fileId}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable("fileId") String fileId, HttpServletRequest request) throws Exception {
        // 这里根据我给定的fileId来下载指定的文件，
        // 如果你想根据其他方式来下载指定文件的话，请自己修改业务逻辑
        // 1. 根据fileId从数据库中获取到指定的文件信息，包括文件名、文件存储地址等等。
        // 1.1 假设我已经获取到了文件信息。
        String fileName = "XXXX";
        String filePath = "XXXX";

        // 2. 解决下载的文件的文件名出现中文乱码
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.contains("MSIE") || userAgent.contains("Trident")) {
            // IE浏览器
            fileName = java.net.URLEncoder.encode(fileName, "UTF-8");
        } else {
            // 非IE浏览器
            fileName = new String(fileName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        }

        // 3. 下载文件
        byte[] data = Files.readAllBytes(Paths.get(filePath));
        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(data.length)
                .body(resource);
    }
}
