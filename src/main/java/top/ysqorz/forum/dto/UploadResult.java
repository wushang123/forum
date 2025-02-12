package top.ysqorz.forum.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * @author passerbyYSQ
 * @create 2021-05-18 16:13
 */
@Setter
@Getter
public class UploadResult {

    // 新的文件名
    private String filename;
    // 文件大小
    private Long totalBytes;
    // 后缀名
    private String suffix;
    // 文件url
    private String[] url; // 可以放多个url。图片可以放不同缩略图的url

    public UploadResult() {
    }

    public UploadResult(String filename, Long totalBytes, String suffix, String... url) {
        this.filename = filename;
        this.totalBytes = totalBytes;
        this.suffix = suffix;
        this.url = url;
    }
}
