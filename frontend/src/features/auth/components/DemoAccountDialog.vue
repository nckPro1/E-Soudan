<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ModalDialog from '../../../shared/components/ModalDialog.vue'
import type { DemoAccount } from '../types'

/**
 * Cửa sổ thông tin tài khoản demo cho người xem sản phẩm: email, mật khẩu (sao chép được), nút điền sẵn vào form đăng nhập
 * và lưu ý đây là tài khoản dùng chung. Tài khoản này là Thành viên có sẵn dữ liệu mẫu của mọi tính năng.
 */
const props = defineProps<{ account: DemoAccount }>()
const emit = defineEmits<{ close: []; fill: [email: string, password: string] }>()

const { t } = useI18n()
const copied = ref<'email' | 'password' | null>(null)

async function copy(field: 'email' | 'password') {
  try {
    await navigator.clipboard.writeText(field === 'email' ? props.account.email : props.account.password)
    copied.value = field
    setTimeout(() => (copied.value = null), 1500)
  } catch {
    // Trình duyệt chặn clipboard: người dùng vẫn bôi đen sao chép tay được
  }
}
</script>

<template>
  <ModalDialog
    :title="t('auth.demo.title')"
    @close="emit('close')"
  >
    <p class="mb-4 text-[0.8125rem] text-[#424242]">
      {{ t('auth.demo.intro') }}
    </p>

    <dl class="mb-4 space-y-3 rounded-lg border border-[#e1dfdd] bg-[#f5f5f5] p-4">
      <div>
        <dt class="text-xs font-semibold text-[#616161]">
          {{ t('auth.email') }}
        </dt>
        <dd class="mt-1 flex items-center justify-between gap-2">
          <code
            class="select-all break-all text-sm font-semibold"
            data-testid="demo-email"
          >{{ account.email }}</code>
          <button
            type="button"
            class="btn shrink-0"
            @click="copy('email')"
          >
            {{ copied === 'email' ? t('ai.copied') : t('ai.copy') }}
          </button>
        </dd>
      </div>
      <div>
        <dt class="text-xs font-semibold text-[#616161]">
          {{ t('auth.password') }}
        </dt>
        <dd class="mt-1 flex items-center justify-between gap-2">
          <code
            class="select-all break-all text-sm font-semibold"
            data-testid="demo-password"
          >{{ account.password }}</code>
          <button
            type="button"
            class="btn shrink-0"
            @click="copy('password')"
          >
            {{ copied === 'password' ? t('ai.copied') : t('ai.copy') }}
          </button>
        </dd>
      </div>
    </dl>

    <ul class="mb-5 list-disc space-y-1 pl-5 text-xs text-[#616161]">
      <li>{{ t('auth.demo.noteRole') }}</li>
      <li>{{ t('auth.demo.noteShared') }}</li>
      <li>{{ t('auth.demo.notePassword') }}</li>
    </ul>

    <div class="flex justify-end gap-2">
      <button
        type="button"
        class="btn"
        @click="emit('close')"
      >
        {{ t('ai.close') }}
      </button>
      <button
        type="button"
        class="btn btn-primary"
        data-testid="demo-fill"
        @click="emit('fill', account.email, account.password)"
      >
        {{ t('auth.demo.fill') }}
      </button>
    </div>
  </ModalDialog>
</template>
