<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import UserAvatar from '../../../shared/components/UserAvatar.vue'
import { toApiError } from '../../../shared/api/http'
import { useProfileStore } from '../../people/profile'
import * as aiApi from '../../ai/api'
import AiSummaryDialog from '../../ai/components/AiSummaryDialog.vue'
import { useAiLang } from '../../ai/useAiCall'
import ChatPane from '../components/ChatPane.vue'
import * as chatApi from '../api'
import { useChatStore } from '../store'
import type { Conversation } from '../types'

/**
 * Một cuộc nhắn riêng. Theo trạng thái lời mời: đang chờ thì hiện thẻ cho từng bên (bên gửi: chờ/thu hồi; bên nhận:
 * chấp nhận/từ chối), đã chấp nhận thì là khung trò chuyện đầy đủ. Lịch sử giữ mãi trên server.
 */
const props = defineProps<{ id: number }>()

const { t, te } = useI18n()
const router = useRouter()
const chat = useChatStore()
const profile = useProfileStore()

const fetched = ref<Conversation | null>(null)
const errorCode = ref('')
const acting = ref(false)
const aiSummary = ref(false)
const aiLang = useAiLang()

/** Ưu tiên bản trong store (luôn mới nhờ WebSocket); chưa có thì dùng bản vừa tải riêng. */
const conversation = computed(() => chat.conversations.find((c) => c.id === props.id) ?? fetched.value)
const other = computed(() => conversation.value?.other ?? null)
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

async function load() {
  errorCode.value = ''
  if (chat.conversations.some((c) => c.id === props.id)) return
  try {
    fetched.value = await chatApi.getConversation(props.id)
  } catch (error) {
    errorCode.value = toApiError(error).code
  }
}
onMounted(load)
watch(() => props.id, load)

async function run(action: () => Promise<unknown>, leave = false) {
  if (acting.value) return
  acting.value = true
  errorCode.value = ''
  try {
    await action()
    if (leave) await router.replace({ name: 'chat' })
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    acting.value = false
  }
}

const accept = () => run(() => chat.accept(props.id))
const decline = () => run(() => chat.decline(props.id), true)
const withdraw = () => run(() => chat.cancel(props.id), true)
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <header class="flex h-14 shrink-0 items-center gap-3 border-b border-[#e1dfdd] px-4">
      <RouterLink
        :to="{ name: 'chat' }"
        class="rounded p-1 text-[#616161] hover:bg-black/5 md:hidden"
        :aria-label="t('chat.title')"
      >
        ←
      </RouterLink>
      <template v-if="other">
        <button
          type="button"
          class="flex min-w-0 items-center gap-3 rounded text-left"
          :aria-label="other.fullName"
          data-testid="chat-header-profile"
          @click="profile.open(other.id)"
        >
          <UserAvatar
            :name="other.fullName"
            :size="32"
          />
          <span class="min-w-0">
            <span class="block truncate text-sm font-bold">{{ other.fullName }}</span>
            <span
              v-if="other.position"
              class="block truncate text-xs text-[#616161]"
            >{{ other.position }}</span>
          </span>
        </button>
      </template>
      <button
        v-if="conversation && conversation.status === 'ACCEPTED'"
        type="button"
        class="ml-auto flex items-center gap-1 rounded px-2 py-1 text-xs font-semibold text-[#4f52b2] hover:bg-[#4f52b2]/10"
        :title="t('ai.summarizeShort')"
        data-testid="ai-summary-button"
        @click="aiSummary = true"
      >
        <span aria-hidden="true">✦</span>{{ t('ai.summarizeShort') }}
      </button>
    </header>

    <AlertBanner
      v-if="errorMessage"
      class="m-4"
    >
      {{ errorMessage }}
    </AlertBanner>

    <template v-if="conversation && other">
      <!-- Chờ chấp nhận -->
      <div
        v-if="conversation.status === 'PENDING'"
        class="flex flex-1 items-center justify-center p-6"
        data-testid="chat-pending"
      >
        <div class="w-full max-w-[26rem] rounded-xl border border-[#e1dfdd] bg-[#f5f5f5] p-6 text-center">
          <div class="mb-3 flex justify-center">
            <UserAvatar
              :name="other.fullName"
              :size="56"
            />
          </div>
          <template v-if="conversation.requestedByMe">
            <p class="mb-1 text-base font-semibold">
              {{ t('chat.requestSentTitle') }}
            </p>
            <p class="mb-4 text-[0.8125rem] text-[#616161]">
              {{ t('chat.requestSentBody', { name: other.fullName }) }}
            </p>
            <button
              type="button"
              class="btn"
              :disabled="acting"
              data-testid="chat-withdraw"
              @click="withdraw"
            >
              {{ t('chat.withdraw') }}
            </button>
          </template>
          <template v-else>
            <p class="mb-1 text-base font-semibold">
              {{ t('chat.requestIncomingTitle', { name: other.fullName }) }}
            </p>
            <p class="mb-4 text-[0.8125rem] text-[#616161]">
              {{ t('chat.requestIncomingBody', { name: other.fullName }) }}
            </p>
            <div class="flex justify-center gap-2">
              <button
                type="button"
                class="btn btn-primary"
                :disabled="acting"
                data-testid="chat-accept"
                @click="accept"
              >
                {{ t('chat.accept') }}
              </button>
              <button
                type="button"
                class="btn"
                :disabled="acting"
                data-testid="chat-decline"
                @click="decline"
              >
                {{ t('chat.decline') }}
              </button>
            </div>
          </template>
        </div>
      </div>

      <!-- Đã chấp nhận -->
      <div
        v-else
        class="min-h-0 flex-1"
      >
        <ChatPane :conversation="conversation" />
      </div>
    </template>

    <AiSummaryDialog
      v-if="aiSummary && other"
      :title="`${t('ai.summarizeShort')}: ${other.fullName}`"
      :call="() => aiApi.summarizeChat(props.id, 24, aiLang)"
      @close="aiSummary = false"
    />
  </div>
</template>
