<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AlertBanner from '../../shared/components/AlertBanner.vue'
import ModalDialog from '../../shared/components/ModalDialog.vue'
import UserAvatar from '../../shared/components/UserAvatar.vue'
import { usePermission } from '../../shared/composables/usePermission'
import { toApiError } from '../../shared/api/http'
import { useAuthStore } from '../auth/store'
import * as chatApi from '../chat/api'
import { useChatStore } from '../chat/store'
import type { Conversation } from '../chat/types'
import * as peopleApi from './api'
import { useProfileStore } from './profile'
import type { Person } from './types'

/**
 * Thẻ hồ sơ một người, mở từ bất kỳ chỗ nào hiện tên hoặc ảnh người đó. Có nút "Nhắn tin" theo đúng luật của trò chuyện:
 * nhắn riêng cần đối phương chấp nhận lời mời, nên nút đổi theo trạng thái (chưa mời / đã gửi lời mời / họ mời mình /
 * đã nhắn được / bị từ chối). Admin không tham gia trò chuyện nên không có nút.
 */
const { t, te } = useI18n()
const router = useRouter()
const profile = useProfileStore()
const chat = useChatStore()
const auth = useAuthStore()
const { isAdmin } = usePermission()

const person = ref<Person | null>(null)
const conversation = ref<Conversation | null>(null)
const loading = ref(false)
const acting = ref(false)
const errorCode = ref('')

const isSelf = computed(() => person.value?.id === auth.user?.id)
const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  return te(`errors.${errorCode.value}`) ? t(`errors.${errorCode.value}`) : t('errors.UNKNOWN')
})

/** Trạng thái của nút nhắn tin theo cuộc trò chuyện hiện có (nếu có). */
type ChatState = 'none' | 'sent' | 'incoming' | 'open' | 'declined'
const state = computed<ChatState>(() => {
  const c = conversation.value
  if (!c) return 'none'
  if (c.status === 'ACCEPTED') return 'open'
  if (c.status === 'PENDING') return c.requestedByMe ? 'sent' : 'incoming'
  // Bị từ chối: bên bị từ chối không mời lại được, bên đã từ chối thì mời lại được
  return c.requestedByMe ? 'declined' : 'none'
})

watch(
  () => profile.userId,
  async (id) => {
    person.value = null
    conversation.value = null
    errorCode.value = ''
    if (id === null) return
    loading.value = true
    try {
      person.value = await peopleApi.getPerson(id)
      if (!isAdmin.value && id !== auth.user?.id) conversation.value = await chatApi.directWith(id)
    } catch (error) {
      errorCode.value = toApiError(error).code
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

async function run(action: () => Promise<Conversation | null>) {
  if (acting.value) return
  acting.value = true
  errorCode.value = ''
  try {
    conversation.value = await action()
  } catch (error) {
    errorCode.value = toApiError(error).code
  } finally {
    acting.value = false
  }
}

/** Bấm "Nhắn tin": gửi lời mời; nếu đã nhắn được rồi thì mở luôn cuộc trò chuyện. */
async function message() {
  const target = person.value
  if (!target) return
  await run(() => chat.startDirect(target.id))
  if (conversation.value?.status === 'ACCEPTED') await openChat()
}

async function openChat() {
  const c = conversation.value
  if (!c) return
  profile.close()
  await router.push({ name: 'chat-conversation', params: { id: c.id } })
}

/** Mở màn hình lời mời đang chờ (bên nhận trả lời ở trang Trò chuyện). */
const accept = () => run(() => chat.accept(conversation.value!.id))
const decline = () =>
  run(async () => {
    await chat.decline(conversation.value!.id)
    return null
  })
const withdraw = () =>
  run(async () => {
    await chat.cancel(conversation.value!.id)
    return null
  })
</script>

<template>
  <ModalDialog
    v-if="profile.userId !== null"
    :title="t('people.profile')"
    @close="profile.close()"
  >
    <p
      v-if="loading"
      class="py-6 text-center text-sm text-[#616161]"
    >
      {{ t('common.loading') }}
    </p>

    <AlertBanner v-if="errorMessage">
      {{ errorMessage }}
    </AlertBanner>

    <div
      v-if="person"
      class="profile"
      data-testid="profile-card"
    >
      <div class="flex items-center gap-4">
        <UserAvatar
          :name="person.fullName"
          :size="64"
        />
        <div class="min-w-0">
          <p class="truncate text-lg font-bold text-[#242424]">
            {{ person.fullName }}
          </p>
          <p
            v-if="person.position"
            class="truncate text-[0.8125rem] text-[#616161]"
          >
            {{ person.position }}
          </p>
          <span class="mt-1 inline-block rounded border border-[#e1dfdd] px-1.5 py-px text-[0.6875rem] text-[#616161]">{{ t(`users.roles.${person.systemRole}`) }}</span>
        </div>
      </div>

      <dl class="mt-4 grid grid-cols-[5rem_1fr] gap-x-3 gap-y-1.5 text-[0.8125rem]">
        <dt class="text-[#616161]">
          {{ t('auth.email') }}
        </dt>
        <dd class="truncate">
          <a
            :href="`mailto:${person.email}`"
            class="link"
          >{{ person.email }}</a>
        </dd>
      </dl>

      <!-- Nhắn tin: chỉ khi không phải mình và không phải Admin -->
      <div
        v-if="!isSelf && !isAdmin"
        class="mt-5 border-t border-[#edebe9] pt-4"
      >
        <template v-if="state === 'none'">
          <button
            type="button"
            class="btn btn-primary w-full"
            :disabled="acting"
            data-testid="profile-message"
            @click="message"
          >
            {{ t('people.message') }}
          </button>
          <p class="mt-2 text-center text-[0.6875rem] text-[#616161]">
            {{ t('people.requestHint') }}
          </p>
        </template>

        <template v-else-if="state === 'sent'">
          <p
            class="text-center text-[0.8125rem] text-[#424242]"
            data-testid="profile-sent"
          >
            {{ t('people.requestSent', { name: person.fullName }) }}
          </p>
          <button
            type="button"
            class="btn mt-3 w-full"
            :disabled="acting"
            data-testid="profile-withdraw"
            @click="withdraw"
          >
            {{ t('people.withdraw') }}
          </button>
        </template>

        <template v-else-if="state === 'incoming'">
          <p class="text-center text-[0.8125rem] text-[#424242]">
            {{ t('people.requestIncoming', { name: person.fullName }) }}
          </p>
          <div class="mt-3 flex gap-2">
            <button
              type="button"
              class="btn btn-primary flex-1"
              :disabled="acting"
              data-testid="profile-accept"
              @click="accept().then(openChat)"
            >
              {{ t('chat.accept') }}
            </button>
            <button
              type="button"
              class="btn flex-1"
              :disabled="acting"
              data-testid="profile-decline"
              @click="decline"
            >
              {{ t('chat.decline') }}
            </button>
          </div>
        </template>

        <template v-else-if="state === 'open'">
          <button
            type="button"
            class="btn btn-primary w-full"
            data-testid="profile-open-chat"
            @click="openChat"
          >
            {{ t('people.openChat') }}
          </button>
        </template>

        <p
          v-else
          class="text-center text-[0.8125rem] text-[#616161]"
          data-testid="profile-declined"
        >
          {{ t('people.requestDeclined', { name: person.fullName }) }}
        </p>
      </div>
    </div>
  </ModalDialog>
</template>
