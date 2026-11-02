package com.yizhangtu.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应结构：data:{total, list}
 */
@Data
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    private long total;
    /** 当前页数据 */
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }

    public static <T> PageResult<T> of(long total, List<T> list) {
        return new PageResult<>(total, list);
    }
}
