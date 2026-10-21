// 토스페이먼츠 SDK v2 스크립트(js.tosspayments.com/v2/standard)의 가짜. shop이 쓰는 결제위젯 부분만 흉내 낸다(ADR 0018).
// `@tosspayments/tosspayments-sdk`의 loadTossPayments(clientKey, { src })가 이 스크립트를 불러 window.TossPayments를 부른다.
// 결제하기(requestPayment)는 가짜 PG의 결제 인증 화면(/checkout)으로 간다.
(function () {
  var base = new URL(document.currentScript.src).origin;

  function render(selector, html) {
    var element = document.querySelector(selector);
    if (!element) {
      return Promise.reject(new Error("[가짜 PG] " + selector + " 요소가 없습니다."));
    }
    element.innerHTML = html;
    return Promise.resolve({ on: function () {}, destroy: function () { element.innerHTML = ""; } });
  }

  function widgets() {
    var amount = null;
    return {
      setAmount: function (value) {
        amount = value.value;
        return Promise.resolve();
      },
      renderPaymentMethods: function (options) {
        return render(
          options.selector,
          '<fieldset data-fake-pg="payment-method"><legend>결제 수단(가짜 PG)</legend>' +
            '<label><input type="radio" name="fake-pg-method" checked> 카드</label></fieldset>',
        );
      },
      renderAgreement: function (options) {
        return render(options.selector, '<p data-fake-pg="agreement">가짜 PG 약관에 동의합니다.</p>');
      },
      requestPayment: function (request) {
        if (amount === null) {
          return Promise.reject(new Error("[가짜 PG] setAmount를 먼저 불러야 합니다."));
        }
        if (!request.orderId || !request.orderName || !request.successUrl || !request.failUrl) {
          return Promise.reject(new Error("[가짜 PG] orderId, orderName, successUrl, failUrl이 필요합니다."));
        }
        var query = new URLSearchParams({
          orderId: request.orderId,
          orderName: request.orderName,
          amount: String(amount),
          successUrl: request.successUrl,
          failUrl: request.failUrl,
        });
        window.location.assign(base + "/checkout?" + query.toString());
        // Redirect 방식이라 화면이 넘어가며 끝난다.
        return new Promise(function () {});
      },
    };
  }

  window.TossPayments = function (clientKey) {
    if (!clientKey) {
      throw new Error("[가짜 PG] 클라이언트 키가 없습니다.");
    }
    return { widgets: widgets };
  };
})();
