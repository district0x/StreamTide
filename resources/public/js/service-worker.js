/* Service Worker for listening to push messages and show notifications */

const image = '/img/layout/logo-icon.svg';

/**
 * "Show a notification when receiving a push event"
 */
self.addEventListener('push', event => {
    let data = event.data.json();
    const options = {
        body: data.options.body,
        icon: image
    }
    event.waitUntil(self.registration.showNotification(data.title, options));
});

/**
 * When clicking on a notification, focus the streamtide window/tab or open a new one if not already opened
 */
self.addEventListener('notificationclick', (event) => {
    const url = self.location.origin;
    event.notification.close();
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true, }).then((windowClients) => {
            const client = windowClients.find(client => client.url.startsWith(url) && 'focus' in client)
            if (client)
                return client.focus();
            if (clients.openWindow) {
                return clients.openWindow(url);
            }
        })
    );
});
