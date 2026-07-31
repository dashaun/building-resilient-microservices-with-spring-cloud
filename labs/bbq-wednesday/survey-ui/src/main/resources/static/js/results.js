(function () {
    'use strict';

    var API_SURVEY = '/survey-service';
    var API_RESULTS = '/results-service';
    var POLL_INTERVAL = 2000;

    var charts = {};
    var questionIds = [];
    var resultsContainer = document.getElementById('results-container');
    var connectionStatus = document.getElementById('connection-status');

    fetchQuestionsAndStartPolling();

    function fetchQuestionsAndStartPolling() {
        fetch(API_SURVEY + '/questions')
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (questions) {
                resultsContainer.innerHTML = '';
                questions.forEach(function (q) {
                    questionIds.push(q.id);
                    renderResultsChart(q);
                });
                connectionStatus.textContent = 'Polling: Active';
                connectionStatus.className = 'status connected';
                pollResults();
                setInterval(pollResults, POLL_INTERVAL);
            })
            .catch(function (err) {
                resultsContainer.innerHTML =
                    '<p class="loading">Error loading questions: ' + err.message + '</p>';
            });
    }

    function renderResultsChart(question) {
        var card = document.createElement('div');
        card.className = 'chart-card';

        var title = document.createElement('h3');
        title.textContent = question.text;
        card.appendChild(title);

        var totalEl = document.createElement('div');
        totalEl.className = 'total-responses';
        totalEl.textContent = 'Total votes: 0';
        totalEl.id = 'total-' + question.id;
        card.appendChild(totalEl);

        var canvas = document.createElement('canvas');
        canvas.id = 'chart-' + question.id;
        card.appendChild(canvas);

        resultsContainer.appendChild(card);

        var ctx = canvas.getContext('2d');
        charts[question.id] = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: question.answers,
                datasets: [{
                    label: 'Votes',
                    data: question.answers.map(function () { return 0; }),
                    backgroundColor: 'rgba(109, 179, 63, 0.7)',
                    borderColor: '#6DB33F',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                indexAxis: 'y',
                scales: {
                    x: { beginAtZero: true, ticks: { stepSize: 1, precision: 0 } }
                },
                plugins: { legend: { display: false } },
                animation: { duration: 400 }
            }
        });
    }

    function pollResults() {
        questionIds.forEach(function (questionId) {
            fetch(API_RESULTS + '/' + questionId)
                .then(function (res) { return res.ok ? res.json() : null; })
                .then(function (data) { if (data) updateChart(data); })
                .catch(function () { /* silently ignore poll errors */ });
        });
    }

    function updateChart(data) {
        var chart = charts[data.questionId];
        if (!chart) return;

        var labels = Object.keys(data.answerCounts);
        var values = labels.map(function (k) { return data.answerCounts[k]; });

        if (labels.length > 0) {
            chart.data.labels = labels;
            chart.data.datasets[0].data = values;
            chart.update();
        }

        var totalEl = document.getElementById('total-' + data.questionId);
        if (totalEl) {
            totalEl.textContent = 'Total votes: ' + data.totalResponses;
        }
    }

})();
