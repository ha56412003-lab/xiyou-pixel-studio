<template>
  <div class="transaction-item">
    <div class="transaction-content">
      <div class="transaction-header">
        <span 
          :class="['transaction-type-tag', getTransactionTypeClass(transaction.transactionType)]"
        >
          {{ getTransactionTypeDesc(transaction.transactionType) }}
        </span>
        <span 
          :class="[
            'transaction-amount',
            getAmountClass(transaction.transactionType)
          ]"
        >
          {{ getAmountDisplay(transaction.transactionType, transaction.amount) }}
        </span>
      </div>
      <div class="transaction-description">{{ transaction.description }}</div>
      <div class="transaction-time">{{ formatTime(transaction.createTime) }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { GetCreditTransactionsApi } from '@/api/user/types'
import { TransactionType } from '@/enums/user'

interface Props {
  transaction: GetCreditTransactionsApi.CreditTransaction
}

 defineProps<Props>()

// Methods
const formatTime = (timeStr: string) => {
  return new Date(timeStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getTransactionTypeDesc = (transactionType: string): string => {
  const typeDescMap: Record<string, string> = {
    [TransactionType.RECHARGE]: '充值积分',
    [TransactionType.CONSUME]: '消费积分',
    [TransactionType.REFUND]: '退还积分',
    [TransactionType.FREEZE]: '冻结积分'
  }
  return typeDescMap[transactionType] || '未知类型'
}

const getTransactionTypeClass = (transactionType: string): string => {
  const typeMap: Record<string, string> = {
    [TransactionType.RECHARGE]: 'recharge',
    [TransactionType.CONSUME]: 'consume', 
    [TransactionType.REFUND]: 'refund',
    [TransactionType.FREEZE]: 'freeze'
  }
  return typeMap[transactionType] || 'default'
}

const getAmountClass = (transactionType: string): string => {
  // 充值和退还显示为收入（绿色），消费和冻结显示为支出（红色）
  if (transactionType === TransactionType.RECHARGE || transactionType === TransactionType.REFUND) {
    return 'income'
  } else {
    return 'expense'
  }
}

const getAmountDisplay = (transactionType: string, amount: number): string => {
  // 充值和退还显示 +，消费和冻结显示 -
  if (transactionType === TransactionType.RECHARGE || transactionType === TransactionType.REFUND) {
    return `+${Math.abs(amount)}`
  } else {
    return `-${Math.abs(amount)}`
  }
}
</script>

<style scoped>
.transaction-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  background: #1b1b1f;
  border-radius: 12px;
  border: 1px solid #2a2a30;
  transition: all 0.3s ease;
}

.transaction-item:hover {
  border-color: rgba(124, 92, 255, 0.45);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
}

.transaction-content {
  flex: 1;
  min-width: 0;
}

.transaction-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.transaction-type-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  text-align: center;
  min-width: 60px;
  border: 1px solid transparent;
}

/* 充值积分 - 绿色 */
.transaction-type-tag.recharge {
  background: rgba(103, 194, 58, 0.14);
  color: #85ce61;
  border-color: rgba(103, 194, 58, 0.35);
}

/* 消费积分 - 红色 */
.transaction-type-tag.consume {
  background: rgba(245, 108, 108, 0.14);
  color: #f78989;
  border-color: rgba(245, 108, 108, 0.35);
}

/* 退还积分 - 蓝色 */
.transaction-type-tag.refund {
  background: rgba(64, 158, 255, 0.14);
  color: #66b1ff;
  border-color: rgba(64, 158, 255, 0.35);
}

/* 冻结积分 - 橙色 */
.transaction-type-tag.freeze {
  background: rgba(230, 162, 60, 0.14);
  color: #eebe77;
  border-color: rgba(230, 162, 60, 0.35);
}

/* 默认样式 */
.transaction-type-tag.default {
  background: rgba(144, 147, 153, 0.14);
  color: #a6a9ad;
  border-color: rgba(144, 147, 153, 0.35);
}

.transaction-amount {
  font-weight: 600;
  font-size: 16px;
}

.transaction-amount.income {
  color: #85ce61;
}

.transaction-amount.expense {
  color: #eebe77;
}

.transaction-description {
  color: #c8c8d0;
  font-size: 13px;
  line-height: 1.3;
  margin-bottom: 2px;
}

.transaction-time {
  color: #8b8b96;
  font-size: 12px;
}

/* Responsive Design */
@media (max-width: 768px) {
  .transaction-item {
    padding: 10px 12px;
    gap: 10px;
  }
  
  .transaction-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style> 