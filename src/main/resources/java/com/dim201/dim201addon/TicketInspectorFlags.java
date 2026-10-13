package com.dim201.dim201addon;

/**
 * 补票员发票消费标志（独立工具类，非 Mixin）
 *
 * 原理：
 *   InvoiceItem.use() 扣金币时会增加 total_cost，tryBuy Mixin 会把 total_cost 增长
 *   误判为"补票员买了普通物品"→ 又给自己发新发票（无限循环）。
 *   通过 ThreadLocal 标志告诉 Mixin "这是发票消费，跳过发票生成"。
 *
 * 为什么独立成工具类：
 *   Mixin 0.8.7 + Fabric Loader 0.18.1 禁止 Mixin 类内出现非 private 静态方法，
 *   所以把 ThreadLocal 和访问方法移到普通 Java 类，不受 Mixin 限制。
 */
public final class TicketInspectorFlags {

    /** 标记当前线程正在执行发票消费操作（防止发票循环生成） */
    private static final ThreadLocal<Boolean> INVOICE_PURCHASING = ThreadLocal.withInitial(() -> false);

    private TicketInspectorFlags() {}

    /** 标记本次扣金币操作是发票消费（在 InvoiceItem.use() 扣钱前调用） */
    public static void markInvoicePurchase() {
        INVOICE_PURCHASING.set(true);
    }

    /** 清除标记（try/finally 保证清除） */
    public static void clearInvoicePurchase() {
        INVOICE_PURCHASING.remove();
    }

    /** Mixin 查询：当前是否正在执行发票消费 */
    public static boolean isInvoicePurchasing() {
        return INVOICE_PURCHASING.get();
    }
}
