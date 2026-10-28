<template>
  <div class="system-container page-container">
    <el-tabs v-model="activeTab" class="system-tabs content-enter" type="card">
      <el-tab-pane :label="t('system.tabs.overview')" name="overview">
        <OverviewView />
      </el-tab-pane>
      <el-tab-pane :label="t('system.tabs.users')" name="users">
        <UserManagementView />
      </el-tab-pane>
      <el-tab-pane :label="t('system.tabs.workflow')" name="workflow">
        <WorkflowManagementView />
      </el-tab-pane>
      <el-tab-pane label="作品管理" name="results">
        <ResultManagementView />
      </el-tab-pane>
      <el-tab-pane :label="t('system.tabs.redemption')" name="redemption">
        <RedemptionCodeView />
      </el-tab-pane>
      <el-tab-pane :label="t('system.tabs.announcement')" name="announcement">
        <AnnouncementView />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import OverviewView from './views/OverviewView.vue'
import UserManagementView from './views/UserManagementView.vue'
import WorkflowManagementView from './views/WorkflowManagementView.vue'
import ResultManagementView from './views/ResultManagementView.vue'
import RedemptionCodeView from './views/RedemptionCodeView.vue'
import AnnouncementView from './views/AnnouncementView.vue'

const { t } = useI18n()
const activeTab = ref('overview')
</script>

<style scoped>
.system-container {
  height: 100%;
  padding: 16px;
  background-color: #111111;
  overflow: auto;
}

.system-tabs {
  height: 100%;
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
}

.system-tabs :deep(.el-tabs__content) {
  height: calc(100% - 55px);
  overflow: auto;
}

.system-tabs :deep(.el-tab-pane) {
  height: 100%;
}

@media (max-width: 992px) {
  .system-container {
    height: auto;
  }
}
</style>

<style>
/* ===== 系统管理页全局深色化（西柚像素工坊） =====
   作用域：.system-container 覆盖所有子视图；poppers/message-box 挂在 body 上，单独全局覆盖 */
.system-container {
  --el-bg-color: #111111;
  --el-bg-color-overlay: #1b1b1f;
  --el-fill-color: #1b1b1f;
  --el-fill-color-light: #232329;
  --el-fill-color-lighter: #1f1f24;
  --el-fill-color-blank: #111111;
  --el-border-color: #2a2a30;
  --el-border-color-light: #2a2a30;
  --el-border-color-lighter: #26262c;
  --el-border-color-extra-light: #24242a;
  --el-border-color-hover: #3a3a44;
  --el-text-color-primary: #f0f0f5;
  --el-text-color-regular: #d5d5de;
  --el-text-color-secondary: #a2a2b0;
  --el-text-color-placeholder: #6d6d7a;
  --el-text-color-disabled: #5a5a66;
  --el-color-primary: #7c5cff;
  --el-color-primary-light-3: #8f76ff;
  --el-color-primary-light-5: #a894ff;
  --el-color-primary-light-7: #3a3460;
  --el-color-primary-light-8: #2e2a4d;
  --el-color-primary-light-9: #241f3d;
  --el-color-primary-dark-2: #6a4ae6;
  --el-mask-color: rgba(0, 0, 0, 0.65);
  color: #f0f0f5;
}

/* ---- Tabs（card 型） ---- */
.system-container .el-tabs__header {
  border-bottom-color: #2a2a30;
}
.system-container .el-tabs__nav-wrap::after {
  background-color: #2a2a30;
}
.system-container .el-tabs--card > .el-tabs__header .el-tabs__item {
  background-color: #1b1b1f;
  border: 1px solid #2a2a30;
  border-bottom: none;
  color: #a2a2b0;
}
.system-container .el-tabs--card > .el-tabs__header .el-tabs__item.is-active {
  background-color: #232329;
  color: #7c5cff;
}
.system-container .el-tabs--card > .el-tabs__header .el-tabs__item:hover {
  color: #f0f0f5;
}
.system-container .el-tabs__nav-scroll {
  background-color: transparent;
}

/* ---- Card ---- */
.system-container .el-card {
  background-color: #1b1b1f;
  border-color: #2a2a30;
  color: #f0f0f5;
}
.system-container .el-card__header {
  border-bottom-color: #2a2a30;
}

/* ---- Table ---- */
.system-container .el-table {
  --el-table-bg-color: #111111;
  --el-table-header-bg-color: #1b1b1f;
  --el-table-tr-bg-color: #111111;
  --el-table-row-hover-bg-color: #232329;
  --el-table-border-color: #2a2a30;
  --el-table-header-text-color: #a2a2b0;
  --el-table-text-color: #f0f0f5;
  color: #f0f0f5;
}
.system-container .el-table th.el-table__cell {
  background-color: #1b1b1f;
  color: #a2a2b0;
}
.system-container .el-table tr,
.system-container .el-table__row {
  background-color: #111111;
}
.system-container .el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background-color: #17171c;
}
.system-container .el-table__body tr.el-table__row:hover > td.el-table__cell {
  background-color: #232329;
}
.system-container .el-table td.el-table__cell,
.system-container .el-table th.el-table__cell.is-leaf {
  border-bottom-color: #2a2a30;
}
.system-container .el-table--border,
.system-container .el-table--border .el-table__cell,
.system-container .el-table__inner-wrapper::before {
  border-color: #2a2a30;
}
.system-container .el-table__empty-block {
  background-color: #111111;
  color: #a2a2b0;
}
.system-container .el-table__fixed::before,
.system-container .el-table__fixed-right::before {
  background-color: #2a2a30;
}

/* ---- Buttons ---- */
.system-container .el-button {
  --el-button-bg-color: #232329;
  --el-button-border-color: #2a2a30;
  --el-button-hover-bg-color: #2a2a32;
  --el-button-hover-border-color: #3a3a44;
  --el-button-text-color: #f0f0f5;
  --el-button-hover-text-color: #ffffff;
  color: #f0f0f5;
}
.system-container .el-button--primary {
  --el-button-bg-color: #7c5cff;
  --el-button-border-color: #7c5cff;
  --el-button-hover-bg-color: #8f76ff;
  --el-button-hover-border-color: #8f76ff;
  --el-button-active-bg-color: #6a4ae6;
  --el-button-active-border-color: #6a4ae6;
  --el-button-disabled-bg-color: #4a3d80;
  --el-button-disabled-border-color: #4a3d80;
  color: #ffffff;
}
.system-container .el-button.is-link {
  background: transparent;
}

/* ---- 分页 ---- */
.system-container .el-pagination {
  --el-pagination-bg-color: #1b1b1f;
  --el-pagination-text-color: #a2a2b0;
  --el-pagination-button-bg-color: #1b1b1f;
  --el-pagination-button-color: #d5d5de;
  --el-pagination-button-disabled-bg-color: #17171c;
  --el-pagination-hover-color: #7c5cff;
}
.system-container .el-pagination .btn-prev,
.system-container .el-pagination .btn-next,
.system-container .el-pagination .el-pager li {
  background-color: #1b1b1f;
  border: 1px solid #2a2a30;
  color: #d5d5de;
}
.system-container .el-pagination .el-pager li.is-active {
  background-color: #7c5cff;
  border-color: #7c5cff;
  color: #ffffff;
}
.system-container .el-pagination .el-select__wrapper,
.system-container .el-pagination .el-input__wrapper {
  background-color: #111111;
  box-shadow: 0 0 0 1px #2a2a30 inset;
  color: #f0f0f5;
}

/* ---- 输入框 / 选择器 / 数字输入 ---- */
.system-container .el-input__wrapper,
.system-container .el-select__wrapper,
.system-container .el-input-number .el-input__wrapper {
  background-color: #111111;
  box-shadow: 0 0 0 1px #2a2a30 inset;
  color: #f0f0f5;
}
.system-container .el-input__inner,
.system-container .el-select__placeholder,
.system-container .el-textarea__inner {
  color: #f0f0f5;
}
.system-container .el-input__wrapper.is-focus,
.system-container .el-select__wrapper.is-focused {
  box-shadow: 0 0 0 1px #7c5cff inset !important;
}
.system-container .el-textarea__inner {
  background-color: #111111;
  box-shadow: 0 0 0 1px #2a2a30 inset;
}
.system-container .el-textarea__inner:focus {
  box-shadow: 0 0 0 1px #7c5cff inset;
}
.system-container .el-input__count-inner {
  background: transparent;
  color: #6d6d7a;
}
.system-container .el-select__selected-item {
  color: #f0f0f5;
}
.system-container .el-select__tags .el-tag {
  background-color: #2e2a4d;
  border-color: #4a3d80;
  color: #c9bcff;
}
.system-container .el-select__caret {
  color: #a2a2b0;
}

/* ---- 表单 ---- */
.system-container .el-form-item__label {
  color: #d5d5de;
}
.system-container .el-checkbox__label,
.system-container .el-radio__label,
.system-container .el-switch__label {
  color: #f0f0f5;
}
.system-container .el-checkbox__inner {
  background-color: #111111;
  border-color: #2a2a30;
}
.system-container .el-checkbox.is-checked .el-checkbox__inner {
  background-color: #7c5cff;
  border-color: #7c5cff;
}
.system-container .el-switch.is-checked .el-switch__core {
  background-color: #7c5cff;
  border-color: #7c5cff;
}
.system-container .el-switch__core {
  background-color: #2a2a30;
  border-color: #2a2a30;
}

/* ---- Tag / Alert / Link ---- */
.system-container .el-tag {
  background-color: #232329;
  border-color: #2a2a30;
  color: #d5d5de;
}
.system-container .el-tag--info {
  background-color: #232329;
  border-color: #2a2a30;
  color: #a2a2b0;
}
.system-container .el-alert--success {
  background-color: #1c2b22;
  border-color: #2c4636;
}
.system-container .el-alert--success .el-alert__title {
  color: #8fd6a8;
}
.system-container .el-text.el-text--info {
  color: #a2a2b0;
}

/* ---- Dialog / Message Box / 日期面板 ---- */
.system-container .el-overlay {
  background-color: rgba(0, 0, 0, 0.65);
}
.system-container .el-dialog {
  background-color: #1b1b1f;
  border: 1px solid #2a2a30;
}
.system-container .el-dialog__title {
  color: #f0f0f5;
}
.system-container .el-dialog__headerbtn .el-dialog__close {
  color: #a2a2b0;
}
.system-container .el-dialog__headerbtn:hover .el-dialog__close {
  color: #7c5cff;
}
.system-container .el-message-box {
  background-color: #1b1b1f;
  border: 1px solid #2a2a30;
}
.system-container .el-message-box__title,
.system-container .el-message-box__content {
  color: #f0f0f5;
}
.system-container .el-message-box__btns .el-button {
  background-color: #232329;
  border-color: #2a2a30;
  color: #f0f0f5;
}
.system-container .el-message-box__btns .el-button--primary {
  background-color: #7c5cff;
  border-color: #7c5cff;
  color: #ffffff;
}
.system-container .el-input-number__decrease,
.system-container .el-input-number__increase {
  background-color: #1b1b1f;
  border-color: #2a2a30;
  color: #a2a2b0;
}
.system-container .el-upload-dragger,
.system-container .el-date-picker__popper,
.system-container .picker__popper {
  background-color: #1b1b1f;
  border-color: #2a2a30;
}
.system-container .el-picker-panel {
  background-color: #1b1b1f;
  border-color: #2a2a30;
  color: #f0f0f5;
}

/* ---- 传送（teleport）到 body 的 popper 与 MessageBox（全局） ---- */
.el-select__popper.el-popper,
.el-picker__popper.el-popper,
.el-message-box__popper {
  background-color: #1b1b1f !important;
  border: 1px solid #2a2a30 !important;
}
.el-select__popper .el-popper__arrow::before,
.el-picker__popper .el-popper__arrow::before {
  background-color: #1b1b1f;
  border-color: #2a2a30;
}
.el-select__popper .el-select-dropdown {
  background-color: #1b1b1f;
}
.el-select__popper .el-select-dropdown__item {
  color: #d5d5de;
}
.el-select__popper .el-select-dropdown__item.is-hovering {
  background-color: #232329;
  color: #f0f0f5;
}
.el-select__popper .el-select-dropdown__item.is-selected {
  color: #7c5cff;
}
.el-select__popper .el-select-dropdown__empty {
  color: #6d6d7a;
}
body .el-message-box {
  background-color: #1b1b1f;
  border: 1px solid #2a2a30;
}
body .el-message-box__title,
body .el-message-box__content {
  color: #f0f0f5;
}
body .el-message-box__btns .el-button {
  background-color: #232329;
  border-color: #2a2a30;
  color: #f0f0f5;
}
body .el-message-box__btns .el-button--primary {
  background-color: #7c5cff;
  border-color: #7c5cff;
  color: #ffffff;
}
</style>