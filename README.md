# Planejador Inteligente de Disciplinas (UFLA)

Este projeto é um sistema que gera automaticamente um plano de estudos personalizado a partir da matriz curricular de um curso da UFLA, respeitando pré-requisitos, limite de créditos e histórico do aluno.

---

## Funcionalidades

* Extração automática da matriz curricular via Selenium
* Construção de grafo de dependências entre disciplinas
* Suporte a:

  * Pré-requisitos fortes
  * Pré-requisitos mínimos
  * Co-requisitos
* Geração automática de plano de períodos
* Consideração do histórico do aluno:

  * Último período concluído
  * Disciplinas reprovadas
* Propagação automática de dependências (disciplinas afetadas por reprovações)
* Respeito ao limite máximo de créditos por período
* Tratamento especial para TCC:

  * Detecção automática (TCC / Estágio / Trabalho de Conclusão)
  * Alocação apenas nos períodos finais
  * Respeito à ordem entre TCC I e TCC II
  * Possibilidade de reservar períodos exclusivos

---

## Lógica do Algoritmo

O sistema modela o curso como um grafo direcionado acíclico (DAG), onde:

* Cada disciplina é um nó
* Cada pré-requisito é uma aresta

### Etapas principais

1. Filtragem

   * Remove disciplinas já concluídas
   * Marca disciplinas inviáveis por dependência de reprovação

2. Ordenação

   * Utiliza ordenação topológica

3. Priorização

   * Critério principal: menor `período máximo`
   * Estratégia: Least Slack First

4. Alocação por período

   * Respeita:

     * Pré-requisitos
     * Co-requisitos (mesmo período)
     * Limite de créditos

5. Tratamento do TCC

   * Separado do fluxo principal
   * Inserido apenas ao final do curso

---

## Exemplo de Entrada

```text
Curso: G010
Matriz: 202301
Último período concluído: 3
Reprovadas: GMM111
Máximo de créditos: 24
Períodos reservados para TCC: 1
```

---

## Exemplo de Saída

```text
Período 4:
- GCC116 | Sistemas Operacionais (4 créditos)
- GCC125 | Redes de Computadores (4 créditos)

Período 8:
- TCC1070 | Trabalho de Conclusão de Curso I

Período 9:
- TCC1071 | Trabalho de Conclusão de Curso II
```

---

## Tecnologias Utilizadas

* Java
* Selenium WebDriver
* Maven
* Estruturas de dados (grafos, BFS, ordenação topológica)

---

## Como Executar

1. Clone o repositório:

```bash
git clone https://github.com/seu-usuario/seu-repo.git
```

2. Instale as dependências com Maven

3. Execute a classe `Main`

4. Informe:

   * Código do curso
   * Código da matriz
   * Histórico acadêmico
   * Configurações desejadas

---

## Observações

* O sistema depende da estrutura atual do SIG UFLA; alterações no site podem afetar o funcionamento
* O ChromeDriver deve estar compatível com a versão do navegador Chrome

---

## Possíveis Melhorias

* Interface gráfica
* Integração com histórico acadêmico real
* Otimização com programação linear inteira
* Consideração de dificuldade das disciplinas
* Balanceamento de carga entre períodos
* Disponibilização via API

---

## Base Teórica

Este projeto se relaciona com:

* Teoria dos Grafos
* Ordenação Topológica
* Problemas de Escalonamento
* Problemas de Satisfação de Restrições

---

## Autor

Caio Ladeira

---

## Licença

Uso acadêmico.
