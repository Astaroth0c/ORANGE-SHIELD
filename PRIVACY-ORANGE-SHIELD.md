# ORANGE SHIELD — Aviso de Privacidade (versão de desenvolvimento)

## Dados processados no aparelho

### Triagem de chamadas
Quando o usuário aceita o papel Android de triagem de chamadas, o sistema entrega ao ORANGE SHIELD os dados da chamada recebida necessários para avaliar o número. O motor calcula sinais de formato, apresentação, frequência e reputação local. O histórico local pode guardar número normalizado, decisão, pontuação, evidências, motivo, versão das regras e horário.

O ORANGE SHIELD não solicita acesso ao histórico telefônico (READ_CALL_LOG) e não declara READ_PHONE_STATE na versão 1.1.

### Guardião WhatsApp
Somente depois de uma explicação em destaque, consentimento afirmativo no app e habilitação manual do Acesso a notificações nas configurações do Android, o serviço observa notificações publicadas pelos pacotes oficiais do WhatsApp e WhatsApp Business. O título e o texto da notificação são analisados em memória para detectar padrões de golpe. O texto original não é persistido. O histórico local guarda remetente, fingerprint SHA-256 do texto, classificação, pontuação, motivo e horário.

O app não controla nem bloqueia diretamente mensagens dentro do WhatsApp. A ação de bloqueio é sempre manual no aplicativo WhatsApp.

## Armazenamento e transmissão
A versão 1.1 realiza a análise localmente e não transmite conteúdo de chamadas ou notificações a servidores. O banco Room e as preferências são armazenados no espaço privado do aplicativo. O backup automático Android está desativado (allowBackup=false).

## Escolha e revogação
O usuário pode recusar qualquer uma das funções e continuar usando as demais. O papel de triagem pode ser removido nas configurações de aplicativos/funções padrão do Android. O acesso a notificações pode ser desligado nas configurações de Acesso a notificações ou o consentimento de análise pode ser revogado dentro da aba WhatsApp.

## Limitações
A disponibilidade de funções depende da versão do Android, fabricante e papéis concedidos. A triagem não recebe metadados de rede/IP de operadoras nem prova criptográfica universal de identidade da origem. Agrupamentos por prefixo são sinais estatísticos e não identificam pessoas ou dispositivos.

## Contato e publicação
Este documento descreve a implementação de desenvolvimento v1.1. Antes de qualquer distribuição pública, o responsável pelo aplicativo deve publicar uma política de privacidade acessível por URL, informar um canal de contato real, revisar retenção/exclusão de dados, completar a seção Segurança dos dados da Play Console e submeter declarações exigidas pelo Google. Este arquivo, sozinho, não substitui essas obrigações.
