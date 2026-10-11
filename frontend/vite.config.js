import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * Vite 配置。
 *
 * <p>这一段最要紧的是 server.proxy —— 它是「开发期怎么解决跨域」的答案。
 *
 * <p>问题：前端跑在 5173，后端跑在 9090，**端口不同就是跨域**，
 * 浏览器默认会拦掉。后端现在也没有配置 CORS。
 *
 * <p>方案：让浏览器只跟 5173 说话。我们发出的 /dev-api/xxx 请求，
 * 由 Vite 这个开发服务器在背后转给 http://localhost:9090/dev-api/xxx。
 * 浏览器眼里这始终是同一次「向 5173 发请求」，压根不存在跨域。
 *
 * <p>注意这里 **没有 rewrite**：后端 application-local.yaml 里写了
 * context-path: /dev-api，也就是说后端自己的路径本来就带 /dev-api。
 * 所以原样转发过去就行，别把前缀去掉 —— 去掉了就变成 404。
 *
 * <p>这套代理只在 `npm run dev` 时生效。以后真要部署上线，
 * 得靠 Nginx 做同样的事，或者后端显式配置 CORS。
 *
 * <p>【server.watch.ignored 是干什么的】
 * 有些编辑器和工具保存文件时，不是原地覆盖，而是「先写一个临时文件、
 * 再原子替换」。临时文件如果落在 src/ 里面，Vite 的文件监听器就会去watch 它；
 * 而它往往刚建好就被改名或删掉了，监听器拿到一个 EBUSY（资源被占用），
 * 这个异常会直接把整个 dev server 打崩。
 *
 * <p>症状是：改一个 .vue 文件，终端里突然甩出一段 EBUSY 堆栈，
 * 然后 http://localhost:5173 就打不开了 —— 看着像 Vite 有 bug，
 * 其实是监听器被自己的临时文件绊倒。
 *
 * <p>这里把「点开头 + tmp」这类临时文件排除掉。规则写窄一点，
 * 不要去忽略整个 src —— 那样热更新就不工作了。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    watch: {
      ignored: ['**/.*.tmp', '**/.*.tmpdir/**', '**/*.tmpdir/**'],
    },
    proxy: {
      '/dev-api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
      },
    },
  },
})
