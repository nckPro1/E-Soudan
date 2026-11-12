import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import en from '../../../shared/locales/en.json'
import { ApiError } from '../../../shared/types/api'
import * as api from '../api'
import { useAuthStore } from '../store'
import LoginView from './LoginView.vue'

vi.mock('../api')

const profile = {
  id: 1,
  email: 'tanaka@company.co.jp',
  fullName: 'Tanaka',
  position: null,
  systemRole: 'USER' as const,
  status: 'ACTIVE' as const,
  mustChangePassword: false,
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
}

const tokens = { accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900, mustChangePassword: false }

async function mountLogin(path = '/login') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/', name: 'home', component: { render: () => null } },
      { path: '/users', name: 'users', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const wrapper = mount(LoginView, { global: { plugins: [router, i18n] } })
  return { wrapper, router }
}

const emailInput = (w: VueWrapper) => w.get('#email')
const passwordInput = (w: VueWrapper) => w.get('#password')
const submitButton = (w: VueWrapper) => w.get('button[type="submit"]')
const banner = (w: VueWrapper) => w.find('[role="alert"]')

async function fillAndSubmit(wrapper: VueWrapper, email = 'tanaka@company.co.jp', password = 'secret-pass') {
  await emailInput(wrapper).setValue(email)
  await passwordInput(wrapper).setValue(password)
  await wrapper.get('form').trigger('submit')
}

describe('LoginView', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.resetAllMocks()
    setActivePinia(createPinia())
  })

  describe('default', () => {
    it('should_showBrandTitleAndEmptyForm_when_opened', async () => {
      const { wrapper } = await mountLogin()

      expect(wrapper.text()).toContain('E-Soudan')
      expect(wrapper.get('h1').text()).toContain('Sign in')
      expect(wrapper.get('h1').text()).toContain('ログイン')
      expect(banner(wrapper).exists()).toBe(false)
      expect((emailInput(wrapper).element as HTMLInputElement).value).toBe('')
      expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
    })

    it('should_offerAllThreeLanguages_when_opened', async () => {
      const { wrapper } = await mountLogin()

      const text = wrapper.get('nav').text()
      expect(text).toContain('日本語')
      expect(text).toContain('Tiếng Việt')
      expect(text).toContain('English')
    })

    it('should_toggleBetweenShowAndHide_when_passwordToggleClicked', async () => {
      const { wrapper } = await mountLogin()
      const toggle = wrapper.get('button.toggle')

      expect(passwordInput(wrapper).attributes('type')).toBe('password')
      expect(toggle.text()).toBe('Show')
      await toggle.trigger('click')
      expect(passwordInput(wrapper).attributes('type')).toBe('text')
      expect(toggle.text()).toBe('Hide')
    })
  })

  describe('submit', () => {
    it('should_goHome_when_loginSucceeds', async () => {
      vi.mocked(api.login).mockResolvedValue(tokens)
      vi.mocked(api.fetchMe).mockResolvedValue(profile)
      vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
      const { wrapper, router } = await mountLogin()

      await fillAndSubmit(wrapper)
      await flushPromises()

      expect(api.login).toHaveBeenCalledWith('tanaka@company.co.jp', 'secret-pass')
      expect(router.currentRoute.value.name).toBe('home')
    })

    it('should_trimEmail_when_submitting', async () => {
      vi.mocked(api.login).mockResolvedValue(tokens)
      vi.mocked(api.fetchMe).mockResolvedValue(profile)
      vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
      const { wrapper } = await mountLogin()

      await fillAndSubmit(wrapper, '  tanaka@company.co.jp  ')
      await flushPromises()

      expect(api.login).toHaveBeenCalledWith('tanaka@company.co.jp', 'secret-pass')
    })

    it('should_goToRedirectTarget_when_redirectQueryIsInternal', async () => {
      vi.mocked(api.login).mockResolvedValue(tokens)
      vi.mocked(api.fetchMe).mockResolvedValue(profile)
      vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
      const { wrapper, router } = await mountLogin('/login?redirect=/users')

      await fillAndSubmit(wrapper)
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/users')
    })

    it('should_ignoreRedirect_when_itPointsOutsideTheApp', async () => {
      vi.mocked(api.login).mockResolvedValue(tokens)
      vi.mocked(api.fetchMe).mockResolvedValue(profile)
      vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
      const { wrapper, router } = await mountLogin('/login?redirect=//evil.com')

      await fillAndSubmit(wrapper)
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('home')
    })
  })

  describe('loading', () => {
    it('should_showSpinnerAndDisableForm_when_requestInFlight', async () => {
      let finish!: () => void
      vi.mocked(api.login).mockReturnValue(new Promise((resolve) => (finish = () => resolve(tokens))))
      vi.mocked(api.fetchMe).mockResolvedValue(profile)
      vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
      const { wrapper } = await mountLogin()

      await fillAndSubmit(wrapper)

      expect(submitButton(wrapper).text()).toContain('Signing in...')
      expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
      expect(wrapper.find('svg.spinner').exists()).toBe(true)
      expect(emailInput(wrapper).attributes('disabled')).toBeDefined()
      expect(passwordInput(wrapper).attributes('disabled')).toBeDefined()

      finish()
      await flushPromises()
    })

    it('should_ignoreSecondSubmit_when_alreadyLoading', async () => {
      let finish!: () => void
      vi.mocked(api.login).mockReturnValue(new Promise((resolve) => (finish = () => resolve(tokens))))
      vi.mocked(api.fetchMe).mockResolvedValue(profile)
      vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
      const { wrapper } = await mountLogin()

      await fillAndSubmit(wrapper)
      await wrapper.get('form').trigger('submit')

      expect(api.login).toHaveBeenCalledTimes(1)
      finish()
      await flushPromises()
    })
  })

  describe('error', () => {
    it('should_showErrorBannerAndMarkFields_when_credentialsWrong', async () => {
      vi.mocked(api.login).mockRejectedValue(new ApiError('INVALID_CREDENTIALS', 401, 'x'))
      const { wrapper } = await mountLogin()

      await fillAndSubmit(wrapper)
      await flushPromises()

      expect(banner(wrapper).text()).toBe('Email or password is incorrect.')
      expect(emailInput(wrapper).attributes('aria-invalid')).toBe('true')
      expect(passwordInput(wrapper).attributes('aria-invalid')).toBe('true')
      // giữ nguyên dữ liệu đã nhập, cho phép thử lại ngay
      expect((emailInput(wrapper).element as HTMLInputElement).value).toBe('tanaka@company.co.jp')
      expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
    })

    it('should_showNetworkMessage_when_serverUnreachable', async () => {
      vi.mocked(api.login).mockRejectedValue(new ApiError('NETWORK_ERROR', 0, 'offline'))
      const { wrapper } = await mountLogin()

      await fillAndSubmit(wrapper)
      await flushPromises()

      expect(banner(wrapper).text()).toContain('Cannot reach the server')
      expect(emailInput(wrapper).attributes('aria-invalid')).toBeUndefined()
    })

    it('should_showGenericMessage_when_errorCodeUnknown', async () => {
      vi.mocked(api.login).mockRejectedValue(new ApiError('SOMETHING_NEW', 500, 'x'))
      const { wrapper } = await mountLogin()

      await fillAndSubmit(wrapper)
      await flushPromises()

      expect(banner(wrapper).text()).toBe('Something went wrong. Please try again.')
    })

    it('should_allowRetry_when_errorShown', async () => {
      vi.mocked(api.login)
        .mockRejectedValueOnce(new ApiError('INVALID_CREDENTIALS', 401, 'x'))
        .mockResolvedValueOnce(tokens)
      vi.mocked(api.fetchMe).mockResolvedValue(profile)
      vi.mocked(api.fetchPermissions).mockResolvedValue({ systemRole: 'USER', permissions: [] })
      const { wrapper, router } = await mountLogin()

      await fillAndSubmit(wrapper)
      await flushPromises()
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('home')
    })
  })

  describe('account locked', () => {
    async function lockedWrapper() {
      vi.mocked(api.login).mockRejectedValue(new ApiError('ACCOUNT_LOCKED', 403, 'x'))
      const mounted = await mountLogin()
      await fillAndSubmit(mounted.wrapper, 'locked.user@company.co.jp')
      await flushPromises()
      return mounted.wrapper
    }

    it('should_showLockedBannerAndDisableEverything_when_accountLocked', async () => {
      const wrapper = await lockedWrapper()

      expect(banner(wrapper).text()).toBe('This account is locked. Please contact your administrator.')
      expect(emailInput(wrapper).attributes('disabled')).toBeDefined()
      expect(passwordInput(wrapper).attributes('disabled')).toBeDefined()
      expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
      expect(submitButton(wrapper).text()).toBe('Sign in')
    })

    it('should_notSubmitAgain_when_locked', async () => {
      const wrapper = await lockedWrapper()

      await wrapper.get('form').trigger('submit')

      expect(api.login).toHaveBeenCalledTimes(1)
    })

    it('should_resetForm_when_userChoosesAnotherAccount', async () => {
      const wrapper = await lockedWrapper()

      await wrapper.get('button.link').trigger('click')

      expect(banner(wrapper).exists()).toBe(false)
      expect(emailInput(wrapper).attributes('disabled')).toBeUndefined()
      expect((emailInput(wrapper).element as HTMLInputElement).value).toBe('')
      expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
    })
  })

  describe('session expired', () => {
    it('should_showNeutralBannerAndPrefillEmail_when_reasonIsExpired', async () => {
      useAuthStore().lastEmail = 'tanaka.k@company.co.jp'
      const { wrapper } = await mountLogin('/login?reason=expired')

      expect(banner(wrapper).text()).toBe('Your session has expired. Please sign in again.')
      expect(banner(wrapper).classes()).toContain('info')
      expect((emailInput(wrapper).element as HTMLInputElement).value).toBe('tanaka.k@company.co.jp')
      expect(passwordInput(wrapper).attributes('autofocus')).toBeDefined()
      expect(emailInput(wrapper).attributes('aria-invalid')).toBeUndefined()
    })

    it('should_replaceBannerWithError_when_loginFailsAfterExpiry', async () => {
      vi.mocked(api.login).mockRejectedValue(new ApiError('INVALID_CREDENTIALS', 401, 'x'))
      const { wrapper } = await mountLogin('/login?reason=expired')

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(banner(wrapper).classes()).toContain('error')
      expect(banner(wrapper).text()).toBe('Email or password is incorrect.')
    })

    it('should_notShowExpiredBanner_when_normalVisit', async () => {
      const { wrapper } = await mountLogin('/login')

      expect(banner(wrapper).exists()).toBe(false)
    })
  })
})
