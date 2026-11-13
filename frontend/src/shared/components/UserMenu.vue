<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { LOCALES, LOCALE_LABELS, setLocale, type Locale } from '../../app/plugins/i18n'
import { useAuthStore } from '../../features/auth/store'
import { useNotificationStore } from '../../features/notification/store'
import UserAvatar from './UserAvatar.vue'

/**
 * Ảnh đại diện góc phải như Teams: bấm mở thẻ nhỏ có tên, vai trò, đổi ngôn ngữ và đăng xuất.
 * Gom việc của tài khoản vào một chỗ để thanh trên cùng gọn.
 */
const emit = defineEmits<{ signOut: [] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const notifications = useNotificationStore()

const open = ref(false)
const root = ref<HTMLElement | null>(null)

function onOutside(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
}
function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}
onMounted(() => {
  document.addEventListener('mousedown', onOutside)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutside)
  document.removeEventListener('keydown', onKey)
})

function choose(value: Locale) {
  setLocale(value)
}
</script>

<template>
  <div
    v-if="auth.user"
    ref="root"
    class="wrap relative"
  >
    <button
      type="button"
      class="avatar-btn flex rounded-full p-0 hover:ring-2 hover:ring-[#4f52b2]"
      :class="{ 'ring-2 ring-[#4f52b2]': open }"
      :aria-label="auth.user.fullName"
      :aria-expanded="open"
      data-testid="user-menu"
      @click="open = !open"
    >
      <UserAvatar
        :name="auth.user.fullName"
        :size="24"
      />
    </button>

    <div
      v-if="open"
      class="card absolute right-0 top-9 z-40 w-[18.75rem] rounded-lg bg-white p-4 shadow-2xl ring-1 ring-black/10"
      role="dialog"
      :aria-label="auth.user.fullName"
    >
      <div class="me flex items-center gap-3 border-b border-[#edebe9] pb-3.5">
        <UserAvatar
          :name="auth.user.fullName"
          :size="48"
        />
        <div class="who flex min-w-0 flex-col text-sm">
          <strong>{{ auth.user.fullName }}</strong>
          <span class="email truncate text-xs text-[#616161]">{{ auth.user.email }}</span>
          <span class="role truncate text-xs text-[#616161]">{{ auth.user.systemRole }}<template v-if="auth.user.position"> · {{ auth.user.position }}</template></span>
        </div>
      </div>

      <div class="lang flex flex-col gap-2 py-3.5">
        <span class="lang-label text-xs font-semibold text-[#616161]">{{ t('nav.language') }}</span>
        <div class="lang-options flex gap-1.5">
          <button
            v-for="item in LOCALES"
            :key="item"
            type="button"
            class="chip h-[1.875rem] flex-1 rounded-full border px-2 text-xs font-semibold"
            :class="locale === item ? 'active border-[#4f52b2] bg-[#4f52b2]/10 text-[#4f52b2]' : 'border-transparent bg-[#f5f5f5] text-[#242424] hover:border-[#e1dfdd]'"
            :aria-pressed="locale === item"
            @click="choose(item)"
          >
            {{ LOCALE_LABELS[item] }}
          </button>
        </div>
      </div>

      <div class="sound flex items-center justify-between pb-3.5">
        <span class="text-xs font-semibold text-[#616161]">{{ t('notif.soundLabel') }}</span>
        <button
          type="button"
          role="switch"
          class="relative h-5 w-9 rounded-full transition-colors"
          :class="notifications.soundOn ? 'bg-[#4f52b2]' : 'bg-[#c8c6c4]'"
          :aria-checked="notifications.soundOn"
          :aria-label="t('notif.soundLabel')"
          data-testid="sound-switch"
          @click="notifications.toggleSound()"
        >
          <span
            class="absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-all"
            :class="notifications.soundOn ? 'left-[1.125rem]' : 'left-0.5'"
          />
        </button>
      </div>

      <button
        type="button"
        class="signout btn w-full"
        data-testid="sign-out"
        @click="emit('signOut')"
      >
        {{ t('auth.signOut') }}
      </button>
    </div>
  </div>
</template>
