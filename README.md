# CEFFX (Direct3D 11 Ultra-Performance Fork) 🚀

Este projeto é um fork do excelente framework [techsenger/ceffx](https://github.com), que migrou o core do JCEF (Java Chromium Embedded Framework) do Swing para o JavaFX. 

O objetivo deste fork foi fins de aprendizado prático e engenharia reversa de baixo nível, focando em **eliminar o gargalo tradicional do OSR (Off-Screen Rendering) por software** e resolver problemas crônicos de concorrência assíncrona gráfica no Windows 10/11.

## 🛠️ O Problema Original
No JCEF/OSR padrão, os frames do Chromium são renderizados na GPU, baixados para a memória RAM (CPU), enviados via JNI como um `ByteBuffer` bruto e pintados na UI via `Canvas` do JavaFX. Esse fluxo gerava:
- Alto consumo de CPU e limitação severa de FPS.
- Exceções de estouro de capacidade do buffer (`IllegalArgumentException: Upload requires N elements`) durante redimensionamentos agressivos ou reprodução de vídeos em alta definição (4K).
- Vulnerabilidades de concorrência do tipo TOCTOU (Time-of-Check to Time-of-Use).

## ⚡ Soluções de Engenharia Aplicadas neste Fork

### 1. Pipeline Acelerado via GPU (Direct3D 11 Interop) 🎮
- **C++ (Nativo):** Implementamos o override do método `OnAcceleratedPaint` na DLL nativa, interceptando o `shared_texture_handle` do Chromium direto na VRAM.
- **Gerenciamento de Pool Privado:** Criamos um dispositivo Direct3D 11 (`ID3D11Device`) isolado no C++ que realiza o clone síncrono da textura via `context_->CopyResource`, sincronizado por um Mutex de hardware da Microsoft (`IDXGIKeyedMutex`).
- **Java (Prism Hacking):** Através de **Reflexão Avançada**, quebramos o encapsulamento de módulos do JDK moderno (Java 21 a 26) para injetar o ponteiro de hardware (`jlong handle`) diretamente no motor gráfico secreto do JavaFX (o **Prism pipeline**), realizando uma cópia *GPU-to-GPU* sem trafegar dados pela CPU.

### 2. Blindagem Geométrica e Coalescência (A Cartada Final) 🛡️
- **Abandono do Canvas:** Substituímos o componente `Canvas` assíncrono por uma arquitetura baseada em `ImageView` + `WritableImage`, garantindo atualizações atômicas de imagem na thread gráfica.
- **Snapshot Isolation:** Criamos um isolamento síncrono no fallback de software que copia os bytes para um array primitivo (`byte[]`) imune a alterações do pool nativo do Chromium em background.
- **Strict Geometric Gate:** Implementamos uma validação geométrica em tempo real na thread do JavaFX Application. Frames cujas dimensões do snapshot divirjam temporariamente do tamanho alocado no Prism são descartados de forma coalescente, tornando estouros de buffer matematicamente impossíveis.

## 📈 Resultados Obtidos (Testes em Vídeo 4K na Radeon RX 580)
- **Consumo de CPU:** Reduzido de picos estressantes para estáveis **~5%**.
- **Consumo de GPU:** Apenas **~7%**, mantendo 60 FPS cravados e reprodução fluida sem engasgos ou quedas de frames (*micro-stuttering*).
- **Estabilidade:** Logs do console 100% limpos e zero crashes em runtime sob regime de resize agressivo.

---
*Agradecimentos ao criador original do CEFFX pelo excelente ponto de partida arquitetural.*
