<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import AlertBanner from '../../../shared/components/AlertBanner.vue'
import { toApiError } from '../../../shared/api/http'
import {
  ALLOWED_EXTENSIONS,
  IMAGE_EXTENSIONS,
  MAX_FILES_PER_POST,
  MAX_FILE_BYTES,
  extensionOf,
  formatBytes,
} from '../../../shared/utils/fileSize'
import EmojiInsertButton from '../../task/components/EmojiInsertButton.vue'
import * as reportApi from '../api'
import type { Attachment, ReportPost } from '../types'

/**
 * Form viết báo cáo, phủ kín khung bên phải như một trang tạo bài viết: tiêu đề ở ô trên, nội dung rich-text (Tiptap)
 * ở ô dưới, thanh công cụ ở đáy (tiêu đề H1–H3, đậm, nghiêng, gạch chân, danh sách, trích dẫn, mã, liên kết, emoji,
 * đính kèm file và ảnh) cùng nút đăng. Hôm nay đã đăng rồi thì nạp lại tiêu đề, nội dung và file cũ để sửa (gỡ được
 * file cũ, thêm được file mới). Nội dung gửi đi là HTML; backend lọc XSS và kiểm tra từng file trước khi lưu.
 */
const props = defineProps<{ communityId: number; today: ReportPost | null }>()
const emit = defineEmits<{ saved: [post: ReportPost]; close: [] }>()

const { t, te } = useI18n()

interface PendingFile {
  key: number
  file: File
  /** Đường dẫn xem trước nếu là ảnh */
  preview: string | null
}

const title = ref(props.today?.title ?? '')
const saving = ref(false)
const error = ref('')
const linkOpen = ref(false)
const linkUrl = ref('')
const linkInput = ref<HTMLInputElement | null>(null)
const titleInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)

/** File đã đính kèm lần trước (còn giữ), file bị gỡ và file mới chọn. */
const existing = ref<Attachment[]>([...(props.today?.attachments ?? [])])
const removedIds = ref<number[]>([])
const pending = ref<PendingFile[]>([])
let pendingKey = 0

const updating = computed(() => props.today !== null)
const attachmentCount = computed(() => existing.value.length + pending.value.length)

const editor = useEditor({
  content: props.today?.body ?? '',
  extensions: [
    // StarterKit đã gồm tiêu đề, đậm, nghiêng, gạch chân, gạch ngang, danh sách, trích dẫn, mã và liên kết
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      link: { openOnClick: false, autolink: true, HTMLAttributes: { rel: 'noopener noreferrer nofollow' } },
    }),
    Placeholder.configure({ placeholder: () => t('report.editorPlaceholder') }),
  ],
  editorProps: {
    attributes: {
      class: 'rich min-h-[14rem] text-[0.875rem] leading-relaxed text-[#242424]',
      'aria-label': t('report.editorPlaceholder'),
      'data-testid': 'report-editor',
    },
  },
})

onMounted(() => titleInput.value?.focus())
onBeforeUnmount(() => {
  editor.value?.destroy()
  pending.value.forEach((p) => p.preview && URL.revokeObjectURL(p.preview))
})

// ---- thanh công cụ ----
interface ToolButton {
  key: string
  label: string
  text?: string
  icon?: string
  active: () => boolean
  run: () => void
}

const ICONS = {
  bullet: 'M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01',
  ordered: 'M10 6h10M10 12h10M10 18h10M4 5l1-.8V9M3.5 15.5c.3-.6 1.2-.8 1.7-.4.6.5.2 1.2-.2 1.6L3.5 18.5h2',
  quote: 'M7 7h4v4c0 3-1.5 4.5-4 5M15 7h4v4c0 3-1.5 4.5-4 5',
  code: 'M8 7l-5 5 5 5M16 7l5 5-5 5',
}

const buttons = computed<ToolButton[]>(() => {
  const ed = editor.value
  if (!ed) return []
  const chain = () => ed.chain().focus()
  return [
    { key: 'h1', label: t('report.tb.h1'), text: 'H1', active: () => ed.isActive('heading', { level: 1 }), run: () => chain().toggleHeading({ level: 1 }).run() },
    { key: 'h2', label: t('report.tb.h2'), text: 'H2', active: () => ed.isActive('heading', { level: 2 }), run: () => chain().toggleHeading({ level: 2 }).run() },
    { key: 'h3', label: t('report.tb.h3'), text: 'H3', active: () => ed.isActive('heading', { level: 3 }), run: () => chain().toggleHeading({ level: 3 }).run() },
    { key: 'bold', label: t('report.tb.bold'), text: 'B', active: () => ed.isActive('bold'), run: () => chain().toggleBold().run() },
    { key: 'italic', label: t('report.tb.italic'), text: 'I', active: () => ed.isActive('italic'), run: () => chain().toggleItalic().run() },
    { key: 'underline', label: t('report.tb.underline'), text: 'U', active: () => ed.isActive('underline'), run: () => chain().toggleUnderline().run() },
    { key: 'strike', label: t('report.tb.strike'), text: 'S', active: () => ed.isActive('strike'), run: () => chain().toggleStrike().run() },
    { key: 'bulletList', label: t('report.tb.bulletList'), icon: ICONS.bullet, active: () => ed.isActive('bulletList'), run: () => chain().toggleBulletList().run() },
    { key: 'orderedList', label: t('report.tb.orderedList'), icon: ICONS.ordered, active: () => ed.isActive('orderedList'), run: () => chain().toggleOrderedList().run() },
    { key: 'quote', label: t('report.tb.quote'), icon: ICONS.quote, active: () => ed.isActive('blockquote'), run: () => chain().toggleBlockquote().run() },
    { key: 'code', label: t('report.tb.code'), icon: ICONS.code, active: () => ed.isActive('code'), run: () => chain().toggleCode().run() },
  ]
})

const linkActive = computed(() => editor.value?.isActive('link') ?? false)

async function toggleLink() {
  if (linkOpen.value) {
    linkOpen.value = false
    return
  }
  linkUrl.value = (editor.value?.getAttributes('link').href as string | undefined) ?? ''
  linkOpen.value = true
  await nextTick()
  linkInput.value?.focus()
}

/** Áp dụng đường dẫn cho đoạn đang chọn; để trống là gỡ liên kết. Tự thêm https:// nếu thiếu giao thức. */
function applyLink() {
  const ed = editor.value
  if (!ed) return
  const raw = linkUrl.value.trim()
  const chain = ed.chain().focus().extendMarkRange('link')
  if (!raw) {
    chain.unsetLink().run()
  } else {
    const href = /^(https?:\/\/|mailto:)/i.test(raw) ? raw : `https://${raw}`
    chain.setLink({ href }).run()
  }
  linkOpen.value = false
}

function removeLink() {
  editor.value?.chain().focus().extendMarkRange('link').unsetLink().run()
  linkOpen.value = false
}

function insertEmoji(emoji: string) {
  editor.value?.chain().focus().insertContent(emoji).run()
}

// ---- đính kèm ----
/** Thêm các file vừa chọn; kiểm tra loại, dung lượng và số lượng giống backend (backend vẫn kiểm tra lại). */
function addFiles(list: FileList | null) {
  if (!list) return
  error.value = ''
  for (const file of Array.from(list)) {
    const ext = extensionOf(file.name)
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      error.value = t('report.fileType', { name: file.name })
      continue
    }
    if (file.size > MAX_FILE_BYTES) {
      error.value = t('report.fileTooLarge', { name: file.name })
      continue
    }
    if (attachmentCount.value >= MAX_FILES_PER_POST) {
      error.value = t('report.tooManyFiles', { max: MAX_FILES_PER_POST })
      break
    }
    pending.value = [
      ...pending.value,
      { key: ++pendingKey, file, preview: IMAGE_EXTENSIONS.includes(ext) ? URL.createObjectURL(file) : null },
    ]
  }
  // Cho phép chọn lại đúng file vừa chọn
  if (fileInput.value) fileInput.value.value = ''
  if (imageInput.value) imageInput.value.value = ''
}

function removePending(item: PendingFile) {
  if (item.preview) URL.revokeObjectURL(item.preview)
  pending.value = pending.value.filter((p) => p.key !== item.key)
}

function removeExisting(item: Attachment) {
  existing.value = existing.value.filter((a) => a.id !== item.id)
  removedIds.value = [...removedIds.value, item.id]
}

// ---- đăng ----
async function submit() {
  const ed = editor.value
  if (!ed || saving.value) return
  if (!title.value.trim()) {
    error.value = t('report.titleRequired')
    titleInput.value?.focus()
    return
  }
  const hasText = ed.getText().trim() !== ''
  if (!hasText && attachmentCount.value === 0) {
    error.value = t('report.emptyBody')
    return
  }
  saving.value = true
  error.value = ''
  try {
    const saved = await reportApi.submitReport(props.communityId, {
      title: title.value.trim(),
      body: hasText ? ed.getHTML() : '',
      files: pending.value.map((p) => p.file),
      removeAttachmentIds: removedIds.value,
    })
    emit('saved', saved)
  } catch (err) {
    const code = toApiError(err).code
    error.value = te(`errors.${code}`) ? t(`errors.${code}`) : t('errors.UNKNOWN')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <!-- Form viết báo cáo phủ kín khung bên phải -->
  <div
    class="composer flex h-full flex-col bg-white"
    data-purpose="report-composer"
    @keydown.ctrl.enter.prevent="submit"
    @keydown.meta.enter.prevent="submit"
  >
    <!-- Đầu form -->
    <div class="flex h-[3.25rem] shrink-0 items-center justify-between border-b border-[#edebe9] px-4">
      <h2 class="text-base font-bold text-[#242424]">
        {{ updating ? t('report.editTitle') : t('report.composeTitle') }}
      </h2>
      <button
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded text-[#616161] hover:bg-black/5 hover:text-black"
        :title="t('report.closeForm')"
        :aria-label="t('report.closeForm')"
        data-testid="close-composer"
        @click="emit('close')"
      >
        <svg
          class="h-4 w-4"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          viewBox="0 0 24 24"
          stroke-linecap="round"
          aria-hidden="true"
        ><path d="M6 18L18 6M6 6l12 12" /></svg>
      </button>
    </div>

    <!-- Ô trên: tiêu đề. Ô dưới: nội dung (cuộn trong khung) -->
    <div class="flex min-h-0 flex-1 flex-col overflow-y-auto px-6 pt-5 max-md:px-3">
      <AlertBanner v-if="error">
        {{ error }}
      </AlertBanner>

      <input
        ref="titleInput"
        v-model="title"
        type="text"
        maxlength="200"
        class="w-full border-0 border-b border-[#e1dfdd] bg-transparent pb-3 text-[1.375rem] font-bold text-[#242424] outline-none placeholder:font-semibold placeholder:text-[#b3b3b3] focus:border-[#5c5fc8]"
        :placeholder="t('report.titlePlaceholder')"
        :aria-label="t('report.titlePlaceholder')"
        data-testid="report-title"
      >

      <EditorContent
        :editor="editor"
        class="flex-1 cursor-text pb-4 pt-4"
        @click="editor?.commands.focus()"
      />
    </div>

    <!-- Đính kèm đã chọn -->
    <div
      v-if="attachmentCount > 0"
      class="flex flex-wrap items-center gap-2 border-t border-[#edebe9] px-6 py-2 max-md:px-3"
      data-testid="composer-attachments"
    >
      <span class="text-xs font-semibold text-[#616161]">{{ t('report.attachments') }} ({{ attachmentCount }}/{{ MAX_FILES_PER_POST }})</span>

      <div
        v-for="a in existing"
        :key="`e${a.id}`"
        class="flex max-w-[14rem] items-center gap-1.5 rounded-lg border border-[#e1dfdd] bg-[#f5f5f5] py-1 pl-2 pr-1 text-xs"
      >
        <span class="truncate font-medium">{{ a.fileName }}</span>
        <span class="shrink-0 text-[11px] text-[#616161]">{{ formatBytes(a.sizeBytes) }}</span>
        <button
          type="button"
          class="flex h-5 w-5 shrink-0 items-center justify-center rounded text-[#616161] hover:bg-black/10"
          :aria-label="`${t('report.removeAttachment')}: ${a.fileName}`"
          @click="removeExisting(a)"
        >
          ×
        </button>
      </div>

      <div
        v-for="p in pending"
        :key="`p${p.key}`"
        class="flex max-w-[14rem] items-center gap-1.5 rounded-lg border border-[#e1dfdd] bg-[#f5f5f5] py-1 pl-1 pr-1 text-xs"
        data-testid="pending-file"
      >
        <img
          v-if="p.preview"
          :src="p.preview"
          :alt="p.file.name"
          class="h-8 w-8 shrink-0 rounded object-cover"
        >
        <svg
          v-else
          class="ml-1 h-5 w-5 shrink-0 text-[#4f52b2]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        ><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5" /></svg>
        <span class="truncate font-medium">{{ p.file.name }}</span>
        <span class="shrink-0 text-[11px] text-[#616161]">{{ formatBytes(p.file.size) }}</span>
        <button
          type="button"
          class="flex h-5 w-5 shrink-0 items-center justify-center rounded text-[#616161] hover:bg-black/10"
          :aria-label="`${t('report.removeAttachment')}: ${p.file.name}`"
          @click="removePending(p)"
        >
          ×
        </button>
      </div>
    </div>

    <!-- Ô nhập đường dẫn -->
    <form
      v-if="linkOpen"
      class="mx-6 mb-2 flex items-center gap-2 rounded-md border border-[#e1dfdd] bg-[#f5f5f5] px-2 py-1.5 max-md:mx-3"
      @submit.prevent="applyLink"
    >
      <input
        ref="linkInput"
        v-model="linkUrl"
        type="text"
        inputmode="url"
        class="h-7 min-w-0 flex-1 rounded border border-[#e1dfdd] bg-white px-2 text-xs outline-none focus:border-[#5c5fc8]"
        :placeholder="t('report.tb.linkUrl')"
        :aria-label="t('report.tb.linkUrl')"
        data-testid="link-input"
      >
      <button
        type="submit"
        class="btn btn-sm btn-primary"
      >
        {{ t('report.tb.applyLink') }}
      </button>
      <button
        v-if="linkActive"
        type="button"
        class="btn btn-sm"
        @click="removeLink"
      >
        {{ t('report.tb.removeLink') }}
      </button>
    </form>

    <!-- Thanh công cụ ở dưới -->
    <div
      class="toolbar flex shrink-0 flex-wrap items-center justify-between gap-1 border-t border-[#edebe9] px-3 py-2"
      role="toolbar"
      :aria-label="t('report.tb.format')"
      data-testid="report-toolbar"
    >
      <div class="flex flex-wrap items-center gap-0.5 text-[#616161]">
        <button
          v-for="b in buttons"
          :key="b.key"
          type="button"
          class="tool flex h-7 min-w-[1.75rem] items-center justify-center rounded px-1 text-xs hover:bg-black/5 hover:text-black"
          :class="b.active() ? 'bg-[#4f52b2]/10 !text-[#4f52b2]' : ''"
          :title="b.label"
          :aria-label="b.label"
          :aria-pressed="b.active()"
          :data-testid="`tool-${b.key}`"
          @mousedown.prevent
          @click="b.run()"
        >
          <span
            v-if="b.text"
            class="font-bold leading-none"
            :class="{ italic: b.key === 'italic', underline: b.key === 'underline', 'line-through': b.key === 'strike' }"
          >{{ b.text }}</span>
          <svg
            v-else
            class="h-4 w-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path :d="b.icon" /></svg>
        </button>

        <button
          type="button"
          class="tool flex h-7 min-w-[1.75rem] items-center justify-center rounded px-1 hover:bg-black/5 hover:text-black"
          :class="linkActive || linkOpen ? 'bg-[#4f52b2]/10 !text-[#4f52b2]' : ''"
          :title="t('report.tb.link')"
          :aria-label="t('report.tb.link')"
          :aria-pressed="linkActive"
          data-testid="tool-link"
          @mousedown.prevent
          @click="toggleLink"
        >
          <svg
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            viewBox="0 0 24 24"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" /></svg>
        </button>

        <EmojiInsertButton @pick="insertEmoji" />

        <!-- Đính kèm file và ảnh -->
        <span class="mx-1 h-4 w-px bg-[#e1dfdd]" />
        <input
          ref="fileInput"
          type="file"
          multiple
          class="hidden"
          :accept="ALLOWED_EXTENSIONS.map((e) => `.${e}`).join(',')"
          data-testid="file-input"
          @change="addFiles(($event.target as HTMLInputElement).files)"
        >
        <input
          ref="imageInput"
          type="file"
          multiple
          class="hidden"
          :accept="IMAGE_EXTENSIONS.map((e) => `.${e}`).join(',')"
          data-testid="image-input"
          @change="addFiles(($event.target as HTMLInputElement).files)"
        >
        <button
          type="button"
          class="flex h-7 w-7 items-center justify-center rounded hover:bg-black/5 hover:text-black"
          :title="t('report.tb.attach')"
          :aria-label="t('report.tb.attach')"
          data-testid="tool-attach"
          @click="fileInput?.click()"
        >
          <svg
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            viewBox="0 0 24 24"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" /></svg>
        </button>
        <button
          type="button"
          class="flex h-7 w-7 items-center justify-center rounded hover:bg-black/5 hover:text-black"
          :title="t('report.tb.image')"
          :aria-label="t('report.tb.image')"
          data-testid="tool-image"
          @click="imageInput?.click()"
        >
          <svg
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            viewBox="0 0 24 24"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><rect
            x="3"
            y="3"
            width="18"
            height="18"
            rx="2"
          /><circle
            cx="8.5"
            cy="8.5"
            r="1.5"
          /><path d="M21 15l-5-5L5 21" /></svg>
        </button>
      </div>

      <div class="flex items-center gap-1.5">
        <button
          type="button"
          class="btn btn-sm"
          @click="emit('close')"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="flex h-7 items-center gap-1.5 rounded bg-[#4f52b2] px-3.5 text-xs font-semibold text-white hover:bg-[#444791] disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="saving"
          :title="t('report.tb.send')"
          :aria-label="t('report.tb.send')"
          data-testid="submit-report"
          @click="submit"
        >
          <span>{{ saving ? t('common.loading') : updating ? t('report.update') : t('report.post') }}</span>
          <svg
            class="h-3.5 w-3.5"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          ><path d="M5 12h14M13 6l6 6-6 6" /></svg>
        </button>
      </div>
    </div>
  </div>
</template>
