/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;
import java.util.List;
/**
 *
 * @author acer
 */
public class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final int total;

    public PageResult(List<T> items, int page, int size, int total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    /** Keeps the requested page number inside 1..totalPages. */
    public static int clampPage(int page, int total, int size) {
        int totalPages = Math.max(1, (total + size - 1) / size);
        return Math.min(Math.max(page, 1), totalPages);
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getTotal() { return total; }
    public int getTotalPages() { return Math.max(1, (total + size - 1) / size); }
}
