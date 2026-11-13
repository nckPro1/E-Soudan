<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatBytes } from '../../../shared/utils/fileSize'
import * as chatApi from '../api'
import type { ChatAttachment } from '../types'

/**
 * File và ảnh đính kèm của một tin nhắn: ảnh hiện thành ô thumbnail (bấm để xem ở tab mới), file khác là chip có tên và
 * dung lượng (bấm để tải). Tải bằng yêu cầu có token (blob) vì chỉ người trong cuộc trò chuyện được tải.
 */
const props = defineProps<{ messageId: number; attachments: ChatAttachment[] }>()

const { t } = useI18n()

const thumbs = ref<Record<number, string>>({})
const failed = ref<Set<number>>(new Set())
const urls: string[] = []

onMounted(async () => {
  await Promise.all(
    props.attachments
      .filter((a) => a.image)
      .map(async (a) => {
        try {
          const blob = await chatApi.downloadAttachment(props.messageId, a.id)
          const url = URL.createObjectURL(blob)
          urls.push(url)
          thumbs.value = { ...thumbs.value, [a.id]: url }
        } catch {
          failed.value = new Set(failed.value).add(a.id)
        }
      }),
  )
})

onBeforeUnmount(() => urls.forEach((u) => URL.revokeObjectURL(u)))

async function download(a: ChatAttachment) {
  const blob = await chatApi.downloadAttachment(props.messageId, a.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = a.fileName
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}

function openImage(a: ChatAttachment) {
  const url = thumbs.value[a.id]
  if (url) window.open(url, '_blank', 'noopener')
}
</script>

<template>
  <div class="attachments mt-1 flex flex-wrap gap-2">
    <template
      v-for="a in attachments"
      :key="a.id"
    >
      <button
        v-if="a.image"
        type="button"
        class="thumb relative h-32 w-32 overflow-hidden rounded-lg border border-[#e1dfdd] bg-[#f5f5f5] hover:ring-2 hover:ring-[#4f52b2]/40"
        :title="`${a.fileName} · ${formatBytes(a.sizeBytes)}`"
        :aria-label="`${t('report.openImage')}: ${a.fileName}`"
        data-testid="chat-attachment-image"
        @click="openImage(a)"
      >
        <img
          v-if="thumbs[a.id]"
          :src="thumbs[a.id]"
          :alt="a.fileName"
          class="h-full w-full object-cover"
        >
        <span
          v-else-if="failed.has(a.id)"
          class="flex h-full items-center justify-center px-2 text-center text-[0.6875rem] text-[#c4314b]"
        >{{ t('report.imageLoadFailed') }}</span>
        <span
          v-else
          class="block h-full w-full animate-pulse bg-[#edebe9]"
        />
      </button>

      <button
        v-else
        type="button"
        class="file flex max-w-[16rem] items-center gap-2 rounded-lg border border-[#e1dfdd] bg-white px-2.5 py-1.5 text-left hover:bg-[#f5f5f5]"
        :title="`${t('report.download')}: ${a.fileName}`"
        data-testid="chat-attachment-file"
        @click="download(a)"
      >
        <svg
          class="h-5 w-5 shrink-0 text-[#4f52b2]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        ><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5" /></svg>
        <span class="min-w-0">
          <span class="block truncate text-xs font-semibold text-[#242424]">{{ a.fileName }}</span>
          <span class="block text-[0.6875rem] text-[#616161]">{{ formatBytes(a.sizeBytes) }}</span>
        </span>
      </button>
    </template>
  </div>
</template>
