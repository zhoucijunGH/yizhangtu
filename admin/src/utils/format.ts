// 展示格式化小工具

/** 金额格式化：千分位 + 两位小数 */
export function fmtMoney(v?: number | null): string {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 重量格式化（公斤），最多两位小数 */
export function fmtWeight(v?: number | null): string {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

/** 计算净重：优先取后端给的 netWeight，否则 毛重 - 皮重 */
export function netWeightOf(row: { grossWeight?: number; tareWeight?: number; netWeight?: number }): number {
  if (row.netWeight != null) return Number(row.netWeight)
  return Number(row.grossWeight || 0) - Number(row.tareWeight || 0)
}

/** 计算金额：优先取后端给的 amount，否则 净重 × 单价 */
export function amountOf(row: { grossWeight?: number; tareWeight?: number; netWeight?: number; price?: number; amount?: number }): number {
  if (row.amount != null) return Number(row.amount)
  return netWeightOf(row) * Number(row.price || 0)
}

/** 今天，格式 YYYY-MM-DD */
export function today(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}
